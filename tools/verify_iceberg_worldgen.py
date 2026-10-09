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
import argparse


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
    ice_blocks = {'minecraft:packed_ice', 'minecraft:blue_ice', 'minecraft:snow_block'}
    chunks = list(read_chunks(directory / 'region'))
    sections = {(c['xPos'], c['zPos']): {s['Y']: section_states(s) for s in c.get('sections', [])}
                for c in chunks}
    pending = {(c['xPos'], c['zPos']) for c in chunks
               if any(packed & 0x8000 for section in c.get('PostProcessing', []) for packed in section)}
    completed = set(sections) - pending
    air = {'Name': 'minecraft:air'}

    def state_at(pos):
        x, y, z = pos
        column = sections.get((x // 16, z // 16))
        assert column is not None, f'{loader}: missing neighboring support chunk at {pos}'
        values = column.get(y // 16)
        return air if values is None else values[(y % 16) * 256 + (z % 16) * 16 + x % 16]

    # A native late disk temporarily changes the block below a supported stone
    # ceiling rim to blue ice, then carves that ice away. Disk's material hook
    # used to recolor the rim immediately, leaving a phantom blue-ice slab.
    # The original stone rim and its stone ceiling must survive unchanged.
    for cx, cz in completed:
        pos = (cx * 16 + 8, 181, cz * 16 + 8)
        state = state_at(pos)
        assert state['Name'] == 'terrain_slabs:terrain_stone_slab' and state.get('Properties', {}).get('type') == 'top', (
            f'{loader}: disk retint changed original terrain at {pos}: {state}')
        assert state_at((pos[0], 182, pos[2]))['Name'] == 'minecraft:stone', f'{loader}: stone ceiling was changed'
        assert state_at((pos[0], 180, pos[2]))['Name'] == 'minecraft:air', f'{loader}: late ice carve did not run'
    print(f'{loader}: {len(completed)} late disk/carve fixtures preserved their original stone rims', flush=True)

    rims = {}
    for chunk_pos, column in sections.items():
        for section_y, states in column.items():
            for index, state in enumerate(states):
                name = state['Name'].removeprefix('terrain_slabs:')
                properties = state.get('Properties', {})
                if name not in counts or properties.get('generated') != 'true':
                    continue
                slab_type = properties.get('type')
                if slab_type not in ('top', 'bottom', 'double'):
                    continue
                pos = (chunk_pos[0] * 16 + index % 16, section_y * 16 + index // 256,
                       chunk_pos[1] * 16 + index // 16 % 16)
                rims[pos] = slab_type
                if chunk_pos in completed and slab_type != 'double':
                    counts[name] += 1

    # Check complete attachment paths, so a group of slabs floating together cannot
    # pass merely because its members touch. Side contact requires matching half faces.
    edges = {pos: [] for pos in rims}
    anchored = set()
    for pos, slab_type in rims.items():
        x, y, z = pos
        neighbors = [(x + 1, y, z), (x - 1, y, z), (x, y, z + 1), (x, y, z - 1)]
        if slab_type in ('top', 'double'):
            neighbors.append((x, y + 1, z))
        if slab_type in ('bottom', 'double'):
            neighbors.append((x, y - 1, z))
        for neighbor in neighbors:
            state = state_at(neighbor)
            if state['Name'] in ice_blocks:
                anchored.add(pos)
                continue
            other = rims.get(neighbor)
            if other is None:
                continue
            touching = (other == 'double' or
                        (neighbor[1] == y and (other == slab_type or slab_type == 'double')) or
                        (neighbor[1] > y and other == 'bottom') or
                        (neighbor[1] < y and other == 'top'))
            if touching:
                edges[pos].append(neighbor)

    reached = set(anchored)
    todo = list(anchored)
    while todo:
        for neighbor in edges[todo.pop()]:
            if neighbor not in reached:
                reached.add(neighbor)
                todo.append(neighbor)
    unsupported = [pos for pos in rims if (pos[0] // 16, pos[2] // 16) in completed and pos not in reached]
    assert len(completed) >= 25, f'{loader}: insufficient completed chunks: {len(completed)}'
    assert counts['packed_ice_slab'] > 0, f'{loader}: fixture generated no packed ice slabs'
    assert counts['blue_ice_slab'] > 0, f'{loader}: fixture generated no blue ice slabs'
    assert not unsupported, f'{loader}: {len(unsupported)} disconnected iceberg rims, examples: {unsupported[:10]}'
    print(f'{loader}: {len(completed)} completed frozen-ocean chunks ({len(pending)} border chunks pending), '
          f'{counts}, zero disconnected iceberg slabs', flush=True)


def prepare_world(run):
    world = run / 'iceberg-regression'
    shutil.rmtree(world, ignore_errors=True)
    pack = world / 'datapacks' / 'iceberg-fixture'
    preset = pack / 'data' / 'iceberg_regression' / 'worldgen' / 'world_preset'
    preset.mkdir(parents=True)
    (pack / 'pack.mcmeta').write_text(json.dumps({'pack': {'pack_format': 48, 'description': 'Frozen-ocean regression fixture'}}))
    # Vanilla blue icebergs occur only once per 200 attempts. Force their frequency in
    # the test datapack so every run exercises overlapping features of both materials.
    placed = pack / 'data' / 'minecraft' / 'worldgen' / 'placed_feature'
    placed.mkdir(parents=True)
    (placed / 'iceberg_blue.json').write_text(json.dumps({
        'feature': 'minecraft:iceberg_blue',
        'placement': [{'type': 'minecraft:rarity_filter', 'chance': 1},
                      {'type': 'minecraft:in_square'}, {'type': 'minecraft:biome'}],
    }))
    # Reproduce disk retinting followed by a later ice-only carve, without
    # depending on third-party mods or redistributing their datapacks.
    fixture_features = pack / 'data' / 'iceberg_regression' / 'worldgen' / 'placed_feature'
    fixture_features.mkdir(parents=True)
    def put_feature(name, feature, y):
        (fixture_features / (name + '.json')).write_text(json.dumps({
            'feature': feature,
            'placement': [{'type': 'minecraft:random_offset', 'xz_spread': 8, 'y_spread': 0},
                          {'type': 'minecraft:height_range', 'height': {'type': 'minecraft:constant', 'value': {'absolute': y}}}],
        }))
    def disk(block, target):
        return {'type': 'minecraft:disk', 'config': {
            'state_provider': {'fallback': {'type': 'minecraft:simple_state_provider', 'state': {'Name': block}}, 'rules': []},
            'radius': 0, 'half_height': 0,
            'target': {'type': 'minecraft:matching_blocks', 'blocks': target},
        }}
    put_feature('stone_ceiling', disk('minecraft:stone', ['minecraft:air']), 182)
    put_feature('stone_floor', disk('minecraft:stone', ['minecraft:air']), 180)
    put_feature('stone_rim', {'type': 'minecraft:simple_block', 'config': {
        'to_place': {'type': 'minecraft:simple_state_provider', 'state': {
            'Name': 'terrain_slabs:terrain_stone_slab',
            'Properties': {'type': 'top', 'waterlogged': 'false', 'generated': 'true'},
        }}}}, 181)
    put_feature('late_ice', disk('minecraft:blue_ice', ['minecraft:stone']), 180)
    put_feature('late_carve', disk('minecraft:air', ['minecraft:blue_ice']), 180)
    biome = pack / 'data' / 'minecraft' / 'worldgen' / 'biome'
    biome.mkdir(parents=True)
    features = [[] for _ in range(11)]
    features[0] = ['iceberg_regression:stone_ceiling', 'iceberg_regression:stone_floor', 'iceberg_regression:stone_rim']
    features[2] = ['minecraft:iceberg_packed', 'minecraft:iceberg_blue']
    features[9] = ['iceberg_regression:late_ice']
    features[10] = ['iceberg_regression:late_carve']
    (biome / 'deep_frozen_ocean.json').write_text(json.dumps({
        'has_precipitation': True, 'temperature': 0.0, 'downfall': 0.5,
        'effects': {'sky_color': 8103167, 'fog_color': 12638463, 'water_color': 3750089, 'water_fog_color': 329011},
        'spawners': {}, 'spawn_costs': {}, 'carvers': {'air': []}, 'features': features,
    }))
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


def server_command(command):
    # Use the server console protocol because Gradle's JavaExec may not forward stdin.
    with socket.create_connection(('127.0.0.1', 25580), timeout=90) as connection:
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
        send(2, command)
        if command == 'stop':
            return ''
        response = receive(struct.unpack('<i', receive(4))[0])
        return response[8:-2].decode('utf-8')


def complete_fixture_chunks(log):
    # Force a wider area through prepareTickingChunk, including both iceberg types.
    # Spawn's smaller completed area may contain only packed ice for a given seed.
    print(server_command('forceload add -96 -96 96 96'), flush=True)
    check = ('execute if loaded -96 64 -96 if loaded -96 64 96 '
             'if loaded 96 64 -96 if loaded 96 64 96 run say iceberg-fixture-ready')
    deadline = time.monotonic() + 120
    while time.monotonic() < deadline:
        server_command(check)
        # /say writes the success marker to the native log, rather than RCON feedback.
        if 'iceberg-fixture-ready' in log.read_text():
            # Chunk availability precedes its queued ticking/postprocessing task.
            time.sleep(5)
            return
        time.sleep(1)
    raise AssertionError('Forced frozen-ocean fixture chunks did not finish loading')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--loader', choices=('fabric', 'neoforge'))
    args = parser.parse_args()
    for loader in ((args.loader,) if args.loader else ('fabric', 'neoforge')):
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
                        complete_fixture_chunks(log)
                        server_command('stop')
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
                if not saved and process.poll() is None:
                    # Preserve native stacks if a load or console command stalls.
                    listing = subprocess.run(['jcmd', '-l'], capture_output=True, text=True, timeout=10)
                    for line in listing.stdout.splitlines():
                        if 'Gradle' in line or 'JCmd' in line:
                            continue
                        pid = line.split()[0]
                        dump = subprocess.run(['jcmd', pid, 'Thread.print'], capture_output=True,
                                              text=True, timeout=10)
                        print(dump.stdout, flush=True)
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
