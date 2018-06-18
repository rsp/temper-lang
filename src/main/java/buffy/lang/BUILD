java_binary(
    name = "buffyc",
    srcs = [
        "Buffyc.java",
    ],
    deps = [
        ":lang",
        "@com_github_pcj_google_options//jar",
    ],
    visibility = [
        "//visibility:public",
    ],
)

java_library(
    name = "lang",
    deps = [
        "@atlassian_commonmark//jar",
        "@com_github_pcj_google_options//jar",
        "@com_google_guava//jar",
        "@com_google_protobuf//jar",
    ],
    srcs = glob(
        [
            "*.java",
            "diagnostic/*.java",
        ],
        exclude=["Buffyc.java"]),
    visibility = [
        "//visibility:public",
    ],
)
