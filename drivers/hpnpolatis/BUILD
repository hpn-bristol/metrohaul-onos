COMPILE_DEPS = CORE_DEPS + JACKSON + [
    "//drivers/utilities:onos-drivers-utilities",
    "//drivers/default:onos-drivers-default",
    "//apps/optical-model:onos-apps-optical-model",
    "//drivers/optical:onos-drivers-optical",
    "@javax_ws_rs_api//jar",
    "//utils/rest:onlab-rest",
    "//protocols/rest/api:onos-protocols-rest-api",
]

BUNDLES = [
    ":onos-drivers-hpnpolatis",
    "//drivers/utilities:onos-drivers-utilities",
]

osgi_jar_with_tests(
    resources = glob(["src/main/resources/**"]),
    resources_root = "src/main/resources",
    test_deps = TEST_ADAPTERS,
    deps = COMPILE_DEPS,
)

APPS = [
    "org.onosproject.restsb",
    "org.onosproject.drivers.optical",
    "org.onosproject.optical-model",
]

onos_app(
    app_name = "org.onosproject.drivers.hpnpolatis",
    category = "Drivers",
    description = "HPN Polatis switch",
    included_bundles = BUNDLES,
    required_apps = APPS,
    title = "HPN Polatis Drivers",
    url = "http://onosproject.org",
)
