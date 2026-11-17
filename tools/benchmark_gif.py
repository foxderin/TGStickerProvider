#!/usr/bin/env python3
"""Host-only GIF parameter experiment. Requires ffmpeg, ffprobe and Pillow.

Synthetic inputs are NOT a representative Telegram corpus or an Android benchmark.
Writes fixtures/results outside the checkout by default. Does not alter app defaults.
"""
import argparse
import hashlib
import json
import math
import platform
import statistics
import subprocess
import sys
import time
from pathlib import Path

from PIL import Image, ImageDraw

PROFILES = {
    # Keep these identical to MainActivity's existing parameters.
    "normal": ["-vf", "scale=320:-1", "-r", "10"],
    "high": ["-lavfi", "split[v],palettegen,[v]paletteuse"],
    # Experimental only: reduce work BEFORE palette generation, cap rather than upscale.
    "candidate": ["-lavfi", "fps=15,scale='min(320,iw)':-1:flags=lanczos,split[a][b];"
                  "[a]palettegen[p];[b][p]paletteuse=dither=bayer:bayer_scale=3"],
}


def run(args):
    result = subprocess.run(args, capture_output=True)
    if result.returncode:
        sys.stderr.write(result.stderr.decode(errors="replace"))
        result.check_returncode()
    return result.stdout


def fixtures(root):
    samples = []
    # 3-second 512px/30fps animations: sparse motion, gradients, transparent edges.
    for kind in ("flat", "gradient", "alpha"):
        folder = root / kind
        folder.mkdir()
        base = Image.new("RGBA", (512, 512), (25, 40, 65, 255))
        if kind == "gradient":
            base.putdata([(x * 255 // 511, y * 255 // 511, (x + y) * 255 // 1022, 255)
                          for y in range(512) for x in range(512)])
        for frame in range(90):
            image = Image.new("RGBA", (512, 512)) if kind == "alpha" else base.copy()
            draw = ImageDraw.Draw(image)
            x = int(180 + 110 * math.sin(frame * math.tau / 90))
            if kind == "alpha":
                draw.ellipse((x - 8, 140, x + 168, 316), fill=(250, 130, 50, 72))
            draw.ellipse((x, 148, x + 160, 308), fill=(250, 130, 50, 255))
            draw.rectangle((x + 38, 180, x + 55, 205), fill=(30, 20, 10, 255))
            draw.rectangle((x + 105, 180, x + 122, 205), fill=(30, 20, 10, 255))
            image.save(folder / f"{frame:03}.png")
        webm = root / f"{kind}.webm"
        run(["ffmpeg", "-v", "error", "-y", "-framerate", "30", "-i", str(folder / "%03d.png"),
             "-c:v", "libvpx-vp9", "-lossless", "1", "-pix_fmt", "yuva420p" if kind == "alpha" else "yuv420p",
             "-threads", "2", str(webm)])
        samples.append(webm)
    return samples


def inspect(path):
    with Image.open(path) as gif:
        frames, duration, transparent = 0, 0, []
        for i in range(gif.n_frames):
            gif.seek(i)
            alpha = gif.convert("RGBA").getchannel("A")
            transparent.append(alpha.histogram()[0])
            duration += gif.info.get("duration", 0)
            frames += 1
        return dict(width=gif.width, height=gif.height, frames=frames, duration_ms=duration,
                    transparent_pixels_min=min(transparent), transparent_pixels_max=max(transparent))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True, help="New output directory, must not already exist")
    parser.add_argument("--repeats", type=int, default=3)
    parser.add_argument("--samples", type=Path, nargs="*", help="Optional real WebM files instead of synthetic fixtures")
    args = parser.parse_args()
    if args.repeats < 1:
        parser.error("--repeats must be positive")
    args.output.mkdir(parents=True, exist_ok=False)
    samples = [s.resolve() for s in args.samples] if args.samples else fixtures(args.output)
    report = dict(host=platform.platform(), ffmpeg=run(["ffmpeg", "-version"]).decode().splitlines()[0],
                  input_mode="supplied" if args.samples else "generated-synthetic",
                  repeats=args.repeats, profiles=PROFILES, results=[])
    # Interleave profiles per repetition to reduce run-order effects. Wall clock includes startup/I/O.
    for index, source in enumerate(samples):
        metadata = json.loads(run(["ffprobe", "-v", "error", "-select_streams", "v:0",
                                   "-show_streams", "-of", "json", str(source)]))
        codec = metadata["streams"][0]["codec_name"]
        if codec not in ("vp8", "vp9"):
            raise ValueError(f"Expected VP8/VP9 WebM, got {codec}: {source}")
        source_sha256 = hashlib.sha256(source.read_bytes()).hexdigest()
        for decoder in ("auto", "libvpx-vp9" if codec == "vp9" else "libvpx"):
            times = {name: [] for name in PROFILES}
            outputs = {name: args.output / f"{index}-{source.stem}-{decoder}-{name}.gif" for name in PROFILES}
            for repetition in range(args.repeats + 1):  # first run is warm-up, excluded
                names = list(PROFILES)
                names = names[repetition % len(names):] + names[:repetition % len(names)]
                for name in names:
                    command = ["ffmpeg", "-v", "error", "-y"]
                    if decoder != "auto":
                        command += ["-c:v", decoder]
                    command += ["-i", str(source)] + PROFILES[name] + ["-f", "gif", str(outputs[name])]
                    started = time.perf_counter()
                    run(command)
                    if repetition:
                        times[name].append(time.perf_counter() - started)
            for name, output in outputs.items():
                row = dict(source=str(source), source_sha256=source_sha256, source_metadata=metadata,
                           decoder=decoder, profile=name, seconds=times[name],
                           median_seconds=statistics.median(times[name]), bytes=output.stat().st_size,
                           **inspect(output))
                report["results"].append(row)
                print(json.dumps(row), flush=True)
    (args.output / "results.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
