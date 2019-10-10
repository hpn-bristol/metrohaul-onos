COMPILE_DEPS = CORE_DEPS + JACKSON + [
    "@javax_ws_rs_api//jar",
    "//utils/rest:onlab-rest",
    "//drivers/utilities:onos-drivers-utilities",
    "//protocols/rest/api:onos-protocols-rest-api",
    "//apps/optical-model:onos-apps-optical-model",
]

TEST_DEPS = TEST_ADAPTERS + [
    "//core/api:onos-api-tests",
]

BUNDLES = [
    ":onos-drivers-hpnpolatis",
    "//drivers/utilities:onos-drivers-utilities",
]

osgi_jar_with_tests(
    resources = glob(["src/main/resources/**"]),
    resources_root = "src/main/resources",
    test_deps = TEST_DEPS,
    deps = COMPILE_DEPS,
)

onos_app(
    app_name = "org.onosproject.drivers.hpnpolatis",
    category = "Drivers",
    description = "HPN Polatis switch",
    included_bundles = BUNDLES,
    required_apps = [
        "org.onosproject.restsb",
        "org.onosproject.drivers.optical",
        "org.onosproject.optical-model",
    ],
    title = "HPN Polatis Drivers",
    url = "http://onosproject.org",
)
