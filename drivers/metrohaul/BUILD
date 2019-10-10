COMPILE_DEPS = CORE_DEPS + [
    '//drivers/utilities:onos-drivers-utilities',
    '//drivers/default:onos-drivers-default',
    '//protocols/netconf/api:onos-protocols-netconf-api',
    '//protocols/netconf/ctl:onos-protocols-netconf-ctl',
    '//apps/config:onos-apps-config',
    '//apps/optical-model:onos-apps-optical-model',
    '//drivers/optical:onos-drivers-optical',
] 

TEST_DEPS = TEST_ADAPTERS + [
    '//core/api:onos-api-tests',
]

BUNDLES = [
    '//drivers/metrohaul:onos-drivers-metrohaul',
]

osgi_jar_with_tests (
    deps = COMPILE_DEPS,
    test_deps = TEST_DEPS,
    resources_root = 'src/main/resources',
    resources = glob(['src/main/resources/**']),
)

APPS = [
    'org.onosproject.netconf',
    'org.onosproject.netconfsb',
    'org.onosproject.optical-model',
    'org.onosproject.drivers.netconf',
    'org.onosproject.drivers.optical'
]

onos_app (
    app_name = 'org.onosproject.drivers.metrohaul',
    title = 'Metro-Haul NETCONF device drivers',
    category = 'Drivers',
    origin = 'CTTC, CNIT',
    url = 'http://metrohaul.eu',
    description = 'ONOS Metro-Haul NETCONF device drivers.',
    included_bundles = BUNDLES,
    required_apps = APPS,

)
