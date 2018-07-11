def java_tests(name, srcs, deps = [], lib_deps = [], test_deps = {}):
    lib_srcs = []
    test_srcs = []
    for src in srcs:
        if src[-9:] == 'Test.java':
            test_srcs.append(src)
        else:
            lib_srcs.append(src)
    common_test_deps = deps
    lib_name = None
    if lib_srcs:
        lib_name = '%s_lib' % name
        native.java_library(
            name = lib_name,
            srcs = lib_srcs,
            deps = lib_deps + deps,
            testonly = 1,
        )
        common_test_deps += [ ':%s' % lib_name ]
    else:
        common_test_deps += lib_deps
    for test_src in test_srcs:
        name = test_src[:-5]
        native.java_test(
            name = name,
            srcs = [ test_src ],
            deps = common_test_deps + test_deps.get(name, []),
        )
