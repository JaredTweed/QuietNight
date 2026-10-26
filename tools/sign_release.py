#!/usr/bin/env python3
"""Sign matching CI/F-Droid APKs locally using the private release key in Secret Service."""

import argparse
import hashlib
import os
from pathlib import Path
import re
import ssl
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
APPLICATION_ID = "dev.jaredtweed.quietnight"
KEY_ALIAS = "quietnight-release"


def sha256(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def sign(reference, reproduced, output, keystore, build_tools):
    if output.exists():
        raise ValueError("Output already exists; published release APKs must never be replaced")
    reference_hash = sha256(reference)
    if reference_hash != sha256(reproduced):
        raise ValueError("CI and F-Droid unsigned APKs differ; resolve reproducibility before signing")

    apksigner = build_tools / "apksigner"
    tool_environment = os.environ.copy()
    if tool_environment.get("JAVA_HOME"):
        tool_environment["PATH"] = str(Path(tool_environment["JAVA_HOME"]) / "bin") + os.pathsep + tool_environment.get("PATH", "")
    badging = subprocess.check_output(
        [str(build_tools / "aapt2"), "dump", "badging", str(reference)],
        env=tool_environment, text=True,
    )
    package = re.search(r"^package: name='([^']+)'", badging, re.MULTILINE)
    if package is None or package[1] != APPLICATION_ID:
        raise ValueError("Only the production Quiet Night application ID can use the release key")
    unsigned = subprocess.run(
        [str(apksigner), "verify", str(reference)],
        env=tool_environment, capture_output=True, text=True,
    )
    if unsigned.returncode == 0:
        raise ValueError("The reference APK must be unsigned")
    if not keystore.is_file():
        raise ValueError("Private release keystore is missing; restore it instead of generating a new key")

    certificate = (ROOT / "fdroid/release-signing-certificate.pem").read_text()
    expected_fingerprint = hashlib.sha256(ssl.PEM_cert_to_DER_cert(certificate)).hexdigest()
    password = subprocess.check_output(
        ["secret-tool", "lookup", "application", "quietnight", "purpose", "release-signing"],
        text=True,
    ).rstrip("\n")
    if not password:
        raise ValueError("Release password is missing from the system keyring")
    signing_environment = tool_environment.copy()
    signing_environment["QUIET_NIGHT_KEYSTORE_PASSWORD"] = password

    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".quietnight-sign-", dir=output.parent) as staging:
        signed = Path(staging) / "release.apk"
        subprocess.run(
            [str(apksigner), "sign", "--ks", str(keystore), "--ks-key-alias", KEY_ALIAS,
             "--ks-pass", "env:QUIET_NIGHT_KEYSTORE_PASSWORD",
             "--key-pass", "env:QUIET_NIGHT_KEYSTORE_PASSWORD",
             # Build-tools 35 otherwise rewrites ZIP padding and breaks signature copying.
             "--alignment-preserved", "true",
             "--v1-signing-enabled", "false", "--v2-signing-enabled", "true",
             "--v3-signing-enabled", "true", "--v4-signing-enabled", "false",
             "--out", str(signed), str(reference)],
            env=signing_environment, check=True,
        )
        report = subprocess.check_output(
            [str(apksigner), "verify", "--verbose", "--print-certs", str(signed)],
            env=tool_environment, text=True,
        )
        fingerprints = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)", report)
        if fingerprints != [expected_fingerprint]:
            raise ValueError("Signing certificate differs from the pinned public release certificate")
        # Publish a complete verified APK atomically, refusing to overwrite even after a race.
        os.link(signed, output)
    print(f"Unsigned SHA-256: {reference_hash}")
    print(f"Certificate SHA-256: {expected_fingerprint}")
    print(f"Signed APK: {output}")
    print(f"Signed SHA-256: {sha256(output)}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference", type=Path, required=True, help="Unsigned tagged GitHub CI APK")
    parser.add_argument("--reproduced", type=Path, required=True, help="Independent clean F-Droid rebuild")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--keystore", type=Path,
                        default=Path.home() / ".local/share/quietnight/signing/release.p12")
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    parser.add_argument("--build-tools", type=Path,
                        default=Path(sdk) / "build-tools/35.0.0" if sdk else None)
    args = parser.parse_args()
    if args.build_tools is None:
        parser.error("Set ANDROID_HOME or pass --build-tools")
    try:
        sign(args.reference.resolve(), args.reproduced.resolve(), args.output.resolve(),
             args.keystore.expanduser().resolve(), args.build_tools.resolve())
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        parser.exit(1, f"Release signing failed: {error}\n")


if __name__ == "__main__":
    main()
