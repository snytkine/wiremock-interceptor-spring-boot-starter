
⏺ There are two ways to release, depending on how automated you want the process to be.

  ---
  Option 1 — Maven Release Plugin (already configured)
  
  This is the standard, fully automated path. It handles version bumping, tagging, and publishing in two steps.

  Step 1: Prepare
  mvn release:prepare
  This will:
  - Ask you for the release version (e.g. 0.0.3) and the next development version (e.g. 0.0.4-SNAPSHOT)
  - Run mvn clean (your preparationGoals)
  - Commit the version bump, create a git tag, and push both to the remote

  Step 2: Perform
  mvn release:perform
  This will:
  - Check out the tag that was just created
  - Run spotless:apply deploy (your goals) with -Prelease automatically activated (via releaseProfiles=release)
  - That means GPG signing, sources JAR, and javadoc JAR are all included
  - central-publishing-maven-plugin handles the upload to Maven Central and waits until published

  ---
  Option 2 — Manual deploy (simpler, no version bumping)
  
  If you just want to deploy whatever version is currently in the pom without the full release machinery:

  mvn deploy -Prelease

  This activates the release profile directly, so GPG signing, sources, and javadoc JARs are included in the upload. You manage the version and git tag
  yourself.

  ---
  Day-to-day development (no change needed)
  mvn release:perform
  This will:
  - Check out the tag that was just created
  - Run spotless:apply deploy (your goals) with -Prelease automatically activated (via releaseProfiles=release)
  - That means GPG signing, sources JAR, and javadoc JAR are all included
  - central-publishing-maven-plugin handles the upload to Maven Central and waits until published

  ---
  Option 2 — Manual deploy (simpler, no version bumping)

  If you just want to deploy whatever version is currently in the pom without the full release machinery:

  mvn deploy -Prelease

  This activates the release profile directly, so GPG signing, sources, and javadoc JARs are included in the upload. You manage the version
  and git tag yourself.

  ---
  Day-to-day development (no change needed)

  mvn verify     # compile, test, coverage check — no GPG required
  mvn test       # just tests

  These never touch the release profile, so they work without a GPG key.
