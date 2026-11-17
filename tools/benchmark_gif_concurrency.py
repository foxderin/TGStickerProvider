#!/usr/bin/env python3
"""Linux host experiment: sequential vs two independent FFmpeg processes.

Requires Python 3, Pillow, psutil, FFmpeg. Uses benchmark_gif.py's unchanged app
profiles. This does not model Android FFmpegKit, shared state or thermal limits.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
from pathlib import Path
import platform
import resource
import subprocess
import threading
import time

import psutil
from benchmark_gif import PROFILES, inspect, run


def batch(sources, profile, workers, folder):
    folder.mkdir()
    active = set()
    lock = threading.Lock()
    stop = threading.Event()
    peak_rss = 0

    def sample():
        nonlocal peak_rss
        while not stop.is_set():
            rss = 0
            with lock:
                for pid in active:
                    try:
                        rss += psutil.Process(pid).memory_info().rss
                    except psutil.NoSuchProcess:
                        pass
            peak_rss = max(peak_rss, rss)
            stop.wait(0.01)

    def convert(job):
        index, source = job
        output = folder / f"{index}.gif"
        command = ["ffmpeg", "-v", "error", "-y", "-i", str(source)] + PROFILES[profile] + ["-f", "gif", str(output)]
        # Unique paths per job: this intentionally excludes shared-target races.
        with (folder / f"{index}.stderr").open("wb") as errors:
            process = subprocess.Popen(command, stdout=subprocess.DEVNULL, stderr=errors)
            with lock:
                active.add(process.pid)
            try:
                code = process.wait()
            finally:
                with lock:
                    active.discard(process.pid)
        if code:
            raise RuntimeError(f"FFmpeg exited {code}: {folder / f'{index}.stderr'}")
        return output

    usage_before = resource.getrusage(resource.RUSAGE_CHILDREN)
    sampler = threading.Thread(target=sample)
    sampler.start()
    started = time.perf_counter()
    try:
        with ThreadPoolExecutor(max_workers=workers) as executor:
            outputs = list(executor.map(convert, enumerate(sources)))
    finally:
        elapsed = time.perf_counter() - started
        stop.set()
        sampler.join()
    usage_after = resource.getrusage(resource.RUSAGE_CHILDREN)
    cpu = usage_after.ru_utime + usage_after.ru_stime - usage_before.ru_utime - usage_before.ru_stime
    # Validation outside timed region; byte identity is stronger than metadata alone.
    return dict(workers=workers, profile=profile, seconds=elapsed, child_cpu_seconds=cpu,
                mean_cpu_cores=cpu / elapsed, sampled_sum_rss_bytes=peak_rss,
                outputs=[dict(sha256=hashlib.sha256(p.read_bytes()).hexdigest(), bytes=p.stat().st_size,
                              **inspect(p)) for p in outputs])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--samples", type=Path, nargs="+", required=True)
    parser.add_argument("--output", type=Path, required=True, help="Must not exist")
    parser.add_argument("--repeats", type=int, default=3)
    args = parser.parse_args()
    if args.repeats < 1:
        parser.error("--repeats must be positive")
    sources = [p.resolve(strict=True) for p in args.samples] * 2
    args.output.mkdir(parents=True, exist_ok=False)
    report = dict(host=platform.platform(), ffmpeg=run(["ffmpeg", "-version"]).decode().splitlines()[0],
                  sources=[dict(path=str(p), sha256=hashlib.sha256(p.read_bytes()).hexdigest()) for p in sources],
                  repeats=args.repeats, results=[])
    references = {}
    for repetition in range(args.repeats + 1):
        for profile in ("normal", "high"):
            for workers in ((1, 2) if repetition % 2 == 0 else (2, 1)):
                row = batch(sources, profile, workers, args.output / f"{repetition}-{profile}-{workers}")
                hashes = [output["sha256"] for output in row["outputs"]]
                if profile not in references:
                    references[profile] = hashes
                row["matches_sequential_reference"] = hashes == references[profile]
                if not row["matches_sequential_reference"]:
                    raise RuntimeError("Output differs from sequential reference")
                row["repetition"] = repetition
                if repetition:  # exclude first pair as warm-up
                    report["results"].append(row)
                print(json.dumps(row), flush=True)
    (args.output / "results.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
