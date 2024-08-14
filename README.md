# bowlby

The [upload-artifact action](https://github.com/actions/upload-artifact) allows you to save files from an actions workflow - stuff like test results.
Unfortunately it's [not yet possible](https://github.com/actions/upload-artifact/issues/14) to actually _look_ at those files without downloading a zip and manually extracting it.
I am _very_ lazy, so that miniscule effort is entirely intolerable when I just want to see what the test failure is.

Enter **bowlby**, an application that does the tedious downloading and unzipping bits and lets you freely browse the contents of the artifacts.

We've got an instance running at http://132.226.129.14:56567/ that allows access to the artifacts in this project.
You can browse the latest test results at
 * [bowlby in isolation](http://132.226.129.14:56567/latest/therealryan/bowlby/testing.yml/flow_execution_reports/app/target/mctf/latest/index.html)
 * [browser in isolation](http://132.226.129.14:56567/latest/therealryan/bowlby/testing.yml/flow_execution_reports/test/target/mctf/browser/latest/index.html)
 * [github in isolation](http://132.226.129.14:56567/latest/therealryan/bowlby/integration.yml/flow_execution_reports/github/latest/index.html)
 * [integrated system](http://132.226.129.14:56567/latest/therealryan/bowlby/integration.yml/flow_execution_reports/e2e/latest/index.html)

If you click through those you'll get sense of _how_ it works and confirmation that it _does_ work.

## Build

It's a standard java/maven project. Check it out and run `mvn package`.
This will produce an executable jar at `/app/target/bowlby` - copy that to wherever you want.

## Execution

Run it with `java -jar bowlby`.
If you're running on linux then you can invoke it [directly](https://github.com/brianm/really-executable-jars-maven-plugin) with `./bowlby`.

By default it'll come up on port `56567` - you should see console output to that effect.

## Usage

Point a browser at the instance (e.g.: if you're running it locally then `http://localhost:56567`) and the root page will show a form that accepts two types of links:
 * Links to specific artifacts, such as you see at the bottom of the summary page of a workflow run (e.g.: `https://github.com/therealryan/bowlby/actions/runs/10391396242/artifacts/1812359413`)
   Bowlby will redirect you to a view of the files in that particular artifact.
   [This set of flows illustrates that operation](http://132.226.129.14:56567/latest/therealryan/bowlby/testing.yml/flow_execution_reports/app/target/mctf/latest/index.html#?inc=chain%3Aartifact)
 * Links to workflows, such as you'd find on the "Actions" tab (e.g.: `https://github.com/therealryan/bowlby/actions/workflows/testing.yml`)
   Bowlby will redirect you to a page with stable links that display the files of the most recent artifacts of that workflow on the repo's default branch.
   [This set of flows illustrates that operation](http://132.226.129.14:56567/latest/therealryan/bowlby/testing.yml/flow_execution_reports/app/target/mctf/latest/index.html#?inc=chain%3Aworkflow)

## Configuration

Bowlby is configured via environment variables with command-line argument overrides:

```
Usage: bowlby [-h] [-a=<artifactValidity>] [-d=<cacheDir>] [-g=<githubApiHost>]
              [-l=<latestValidity>] [-p=<port>] [-r=<repositories>]
              [-t=<authToken>]
A browsable proxy for github action artifacts
  -a, --artifactValidity=<artifactValidity>
                            An ISO-8601 duration string, controlling how long
                              an artifact zip is preserved for.
                            Defaults to 'P3D', which means a downloaded
                              artifact zip will be deleted 3 days after its
                              most recent access.
                            Overrides environment variable
                              'BOWLBY_ARTIFACT_VALIDITY'
  -d, --dir=<cacheDir>      The directory in which to cache artifacts.
                            Defaults to 'bowlby' under the system's temp
                              directory.
                            Overrides environment variable 'BOWLBY_DIR'
  -g, --github=<githubApiHost>
                            The hostname to target with github API requests.
                            Defaults to 'https://api.github.com'
                            Overrides environment variable 'BOWLBY_GH_HOST'
  -h, --help                Prints help and exits
  -l, --latestValidity=<latestValidity>
                            An ISO-8601 duration string, controlling how long
                              the latest artifact results are cached for.
                            Defaults to 'PT10M', which means it could take up
                              to 10 minutes for the link to the latest artifact
                              to reflect the results of a new run.
                            Overrides environment variable
                              'BOWLBY_LATEST_VALIDITY'
  -p, --port=<port>         The port at which to serve artifact contents.
                            Defaults to 56567
                            Overrides environment variable 'BOWLBY_PORT'
  -r, --repos=<repositories>
                            A comma-separated list of 'owner/repo' pairs,
                              identifying the set of repositories that bowlby
                              will serve artifacts from.
                            Defaults to empty, which means _all_ repos will be
                              served.
                            Overrides environment variable 'BOWLBY_GH_REPOS'
  -t, --token=<authToken>   The github authorisation token to present on API
                              requests.
                            Overrides environment variable
                              'BOWLBY_GH_AUTH_TOKEN'
```

Note that:
 * You'll need to give it an API auth token. A [fine-grained personal access token](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens#fine-grained-personal-access-tokens) with no special permissions works fine.
 * If you're going to run an instance exposed to the internet, I'd strongly recommend limiting which repositories are addressable via `BOWLBY_GH_REPOS`.
