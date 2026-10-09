"""Generate frozen-ocean chunks in each loader and check saved iceberg slab support."""
import gzip
import io
import json
import os
from pathlib import Path
import shutil
import signal
import socket
import struct
import subprocess
import time
import zlib


def read_nbt(data):
    stream = io.BytesIO(data)

    def number(fmt):
        return struct.unpack(fmt, stream.read(struct.calcsize(fmt)))[0]

    def string():
        return stream.read(number('>H')).decode('utf-8')

    def value(kind):
        if kind in (1, 2, 3, 4, 5, 6):
            return number({1: '>b', 2: '>h', 3: '>i', 4: '>q', 5: '>f', 6: '>d'}[kind])
        if kind == 7:
            return stream.read(number('>i'))
        if kind == 8:
            return string()
        if kind == 9:
            element_kind, size = number('>B'), number('>i')
            return [value(element_kind) for _ in range(size)]
        if kind == 10:
            result = {}
            while (element_kind := number('>B')) != 0:
                name = string()
                result[name] = value(element_kind)
            return result
        if kind in (11, 12):
            return [number('>i' if kind == 11 else '>Q') for _ in range(number('>i'))]
        raise AssertionError(f'Unsupported NBT type {kind}')

    assert number('>B') == 10, 'Chunk root must be an NBT compound'
    string()
    return value(10)


def read_chunks(directory):
    for file in directory.glob('*.mca'):
        data = file.read_bytes()
        for offset in range(0, 4096, 4):
            sector = int.from_bytes(data[offset:offset + 3], 'big')
            if not sector:
                continue
            start = sector * 4096
            length = int.from_bytes(data[start:start + 4], 'big')
            compression = data[start + 4]
            payload = data[start + 5:start + 4 + length]
            assert not compression & 128, 'External chunk streams are not expected in this fixture'
            chunk = read_nbt({1: gzip.decompress, 2: zlib.decompress, 3: lambda p: p}[compression](payload))
            if chunk.get('Status') == 'minecraft:full':
                yield chunk


def section_states(section):
    block_states = section.get('block_states', {'palette': [{'Name': 'minecraft:air'}]})
    palette = block_states['palette']
    if len(palette) == 1:
        return [palette[0]] * 4096
    bits = max(4, (len(palette) - 1).bit_length())
    per_long = 64 // bits
    mask = (1 << bits) - 1
    packed = block_states['data']
    return [palette[(packed[i // per_long] >> ((i % per_long) * bits)) & mask] for i in range(4096)]


def verify_world(directory, loader):
    counts = {'packed_ice_slab': 0, 'blue_ice_slab': 0, 'snow_slab': 0}
    unsupported = []
    chunks = 0
    empty = {'minecraft:air', 'minecraft:cave_air', 'minecraft:void_air', 'minecraft:water',
             'minecraft:bubble_column', 'minecraft:snow'}
    for chunk in read_chunks(directory / 'region'):
        chunks += 1
        sections = {s['Y']: section_states(s) for s in chunk.get('sections', [])}
        for section_y, states in sections.items():
            for index, state in enumerate(states):
                name = state['Name'].removeprefix('terrain_slabs:')
                properties = state.get('Properties', {})
                if name not in counts or properties.get('generated') != 'true':
                    continue
                slab_type = properties.get('type')
                if slab_type not in ('top', 'bottom'):
                    continue
                counts[name] += 1
                y = section_y * 16 + index // 256
                support_y = y + (1 if slab_type == 'top' else -1)
                support_section = sections.get(support_y // 16)
                assert support_section is not None, 'Slab support section must be saved'
                support = support_section[(support_y % 16) * 256 + index % 256]
                if support['Name'] in empty:
                    unsupported.append((chunk['xPos'] * 16 + index % 16, y,
                                        chunk['zPos'] * 16 + index // 16 % 16, state, support))
    assert chunks >= 25, f'{loader}: insufficient generated chunks: {chunks}'
    assert counts['packed_ice_slab'] + counts['blue_ice_slab'] > 0, f'{loader}: fixture generated no ice slabs'
    assert not unsupported, f'{loader}: {len(unsupported)} unsupported iceberg rims, examples: {unsupported[:10]}'
    print(f'{loader}: {chunks} frozen-ocean chunks, {counts}, zero unsupported iceberg slabs', flush=True)


def prepare_world(run):
    world = run / 'iceberg-regression'
    shutil.rmtree(world, ignore_errors=True)
    pack = world / 'datapacks' / 'iceberg-fixture'
    preset = pack / 'data' / 'iceberg_regression' / 'worldgen' / 'world_preset'
    preset.mkdir(parents=True)
    (pack / 'pack.mcmeta').write_text(json.dumps({'pack': {'pack_format': 48, 'description': 'Frozen-ocean regression fixture'}}))
    dimensions = {
        'minecraft:overworld': {'type': 'minecraft:overworld', 'generator': {'type': 'minecraft:noise',
            'settings': 'minecraft:overworld', 'biome_source': {'type': 'minecraft:fixed', 'biome': 'minecraft:deep_frozen_ocean'}}},
        'minecraft:the_nether': {'type': 'minecraft:the_nether', 'generator': {'type': 'minecraft:noise',
            'settings': 'minecraft:nether', 'biome_source': {'type': 'minecraft:multi_noise', 'preset': 'minecraft:nether'}}},
        'minecraft:the_end': {'type': 'minecraft:the_end', 'generator': {'type': 'minecraft:noise',
            'settings': 'minecraft:end', 'biome_source': {'type': 'minecraft:the_end'}}},
    }
    (preset / 'frozen_ocean.json').write_text(json.dumps({'dimensions': dimensions}))
    (run / 'eula.txt').write_text('eula=true\n')
    (run / 'server.properties').write_text(
        'level-name=iceberg-regression\nlevel-type=iceberg_regression:frozen_ocean\n'
        'level-seed=8675309\ninitial-enabled-packs=vanilla,file/iceberg-fixture\n'
        'online-mode=false\nserver-ip=127.0.0.1\nserver-port=25579\n'
        'enable-rcon=true\nrcon.port=25580\nrcon.password=iceberg-regression\n'
        'view-distance=3\nsimulation-distance=3\nmax-tick-time=120000\n')
    return world


def stop_server():
    # Use the server console protocol because Gradle's JavaExec may not forward stdin.
    with socket.create_connection(('127.0.0.1', 25580), timeout=10) as connection:
        def send(kind, command):
            packet = struct.pack('<ii', 1, kind) + command.encode() + b'\0\0'
            connection.sendall(struct.pack('<i', len(packet)) + packet)

        def receive(size):
            result = b''
            while len(result) < size:
                part = connection.recv(size - len(result))
                assert part, 'Server closed RCON before authentication'
                result += part
            return result

        send(3, 'iceberg-regression')
        packet = receive(struct.unpack('<i', receive(4))[0])
        assert struct.unpack('<i', packet[:4])[0] == 1, 'Fixture console authentication failed'
        send(2, 'stop')


def main():
    for loader in ('fabric', 'neoforge'):
        run = Path(loader) / 'run'
        run.mkdir(parents=True, exist_ok=True)
        worlds = [prepare_world(run), prepare_world(Path('run'))]
        log = Path(f'{loader}-iceberg-worldgen.log')
        saved = False
        with log.open('w') as output:
            process = subprocess.Popen(['bash', './gradlew', f':{loader}:runServer', '--no-daemon', '--console=plain'],
                                       stdin=subprocess.DEVNULL, stdout=output, stderr=subprocess.STDOUT,
                                       start_new_session=True, text=True)
            try:
                deadline = time.monotonic() + 360
                while process.poll() is None and time.monotonic() < deadline:
                    text = log.read_text()
                    if ')! For help, type' in text:
                        stop_server()
                        save_deadline = time.monotonic() + 90
                        while time.monotonic() < save_deadline:
                            saved = 'All dimensions are saved' in log.read_text()
                            if saved or process.poll() is not None:
                                break
                            time.sleep(1)
                        assert saved, f'{loader}: server did not finish saving the generated world'
                        break
                    time.sleep(1)
                assert saved, f'{loader}: server did not generate and save the fixture cleanly'
            finally:
                if process.poll() is None:
                    os.killpg(process.pid, signal.SIGTERM)
                    try:
                        process.wait(timeout=10)
                    except subprocess.TimeoutExpired:
                        os.killpg(process.pid, signal.SIGKILL)
                        process.wait()
                print(log.read_text(), flush=True)
        assert 'Failed to parse level-type' not in log.read_text(), 'Custom frozen-ocean preset was not loaded'
        generated = [world for world in worlds if (world / 'level.dat').exists()]
        assert len(generated) == 1, f'{loader}: expected one saved fixture world, got {generated}'
        verify_world(generated[0], loader)


if __name__ == '__main__':
    main()
