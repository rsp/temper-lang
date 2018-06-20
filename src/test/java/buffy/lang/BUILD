load("//src/test/java/buffy/lang/build_defs:java_tests.bzl", "java_tests")

java_tests(
    name = "tests",
    srcs = glob(["**/*.java"]),
    deps = [
        "//src/main/java/buffy/lang",
        "@com_google_guava//jar",
        "@junit//jar",
    ],
    test_deps = {
        "gather/FileSystemFetcherTest": [
            "@jimfs//jar",
        ],
        "cli/FlagOptionsTest": [
            "@com_github_pcj_google_options//jar",
        ],
    },
)
