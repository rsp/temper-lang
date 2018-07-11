BINARY_SRCS = [
    "Temperc.java",
]

java_binary(
    name = "temperc",
    srcs = BINARY_SRCS,
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
        "@com_github_pcj_google_options//jar",
        "@com_google_guava//jar",
        "@com_google_protobuf//jar",
        "@flexmark//jar",
        "@flexmark_anchorlink//jar",
        "@flexmark_util//jar",
        "@jsoup//jar",
        "@jsr305//jar",
    ],
    srcs = glob(
        [
            "*.java",
            "ast/*.java",
            "cli/*.java",
            "diagnostic/*.java",
            "gather/*.java",
        ],
        exclude=BINARY_SRCS),
    visibility = [
        "//visibility:public",
    ],
)
