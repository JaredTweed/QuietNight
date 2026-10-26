# Developer-signed reproducible releases

Quiet Night uses a developer release certificate so the same verified APK can be distributed through GitHub and F-Droid. F-Droid rebuilds the tagged source and verifies the signed reference APK before publication. The `Binaries` URL and `AllowedAPKSigningKeys` fingerprint are pinned in the build recipe. The public certificate is `release-signing-certificate.pem`.

Certificate SHA-256:

`f90cc5f530bc227418eb1d0a016a790b052e6591a5c038a9979ab6e5544a4e2f`

## Private signing material

The encrypted PKCS12 keystore is stored outside the repository at `~/.local/share/quietnight/signing/release.p12`, alias `quietnight-release`. Its directory has mode 0700 and the file has mode 0600. The password is stored in the desktop Secret Service keyring with attributes `application=quietnight` and `purpose=release-signing`. The key is RSA-4096 with a 10,000-day certificate validity.

Keep an offline backup of the keystore and retain its password in a password manager. Future updates require this key. The public certificate can be shared; the private keystore and password must stay out of Git, CI, releases, and issue comments. Private keys are not uploaded to GitHub Actions.

## Release procedure

1. Bump the version and changelog, complete app checks, and publish a new immutable `quietnight-vVERSION` source tag. Never move a published tag or replace a published APK.
2. Download that tag's unsigned APK from the GitHub release-build workflow.
3. Pin the same full source commit in the F-Droid recipe and obtain an independent clean F-Droid rebuild. Keep source and binary scanners enabled.
4. Use Python 3.11+, JDK 17, Android SDK build-tools 35.0.0, and `secret-tool` to sign locally. For example:

```sh
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
python3 tools/sign_release.py \
  --reference /path/to/github-ci-unsigned.apk \
  --reproduced /path/to/fdroid-rebuilt-unsigned.apk \
  --output .fdroid-release/QuietNight-2.1.0.apk
```

The helper refuses different unsigned bytes, debug application IDs, already signed inputs, an incorrect certificate, and replacement of an existing output. It uses `apksigner --alignment-preserved true`: build-tools 35 otherwise rewrites ZIP alignment padding and prevents signature copying. The complete APK is verified before it is atomically made available at the output path. Passwords are passed through a process environment variable, never command-line arguments or output.

5. Verify signature copying with `apksigcopier` 1.1.1 and Android `apksigner` on the PATH:

```sh
apksigcopier compare --unsigned \
  .fdroid-release/QuietNight-2.1.0.apk /path/to/fdroid-rebuilt-unsigned.apk
```

6. Publish `QuietNight-VERSION.apk`, the public certificate, and checksums as assets of the existing matching GitHub source tag. Confirm the public download's SHA-256 matches the verified local file. The F-Droid reference URL is `https://github.com/JaredTweed/QuietNight/releases/download/quietnight-v%v/QuietNight-%v.apk`.
7. Check the F-Droid pipeline's actual reproducibility result before reporting success. Do not work around a mismatch by disabling checks or substituting the reference binary for the source rebuild.

See [F-Droid's reproducibility documentation](https://f-droid.org/docs/Reproducible_Builds/) and [the signing-tool compatibility notes](https://github.com/obfusk/apksigcopier#what-about-signatures-made-by-apksigner-from-build-tools--3500-rc1).
