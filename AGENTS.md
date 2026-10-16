# Release delivery

The repository owner wants app changes delivered as installable releases, not left only in draft pull requests.

For completed app changes, increment the Android versionCode and versionName, run the relevant tests, merge the reviewed changes, and use the existing GitHub Actions workflow to build with the permanent signing key. Verify the workflow succeeds, then publish a versioned GitHub release containing the signed release APK and concise release notes. Never expose signing secrets or substitute a debug signing key. Report the release link and any untested device behavior. Do not publish a failed build.
