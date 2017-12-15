package ch.ti8m.channelsuite.eurekaclient

data class EurekaConfig(val serviceRegistryUrl: String,
                        val serviceName: String,
                        val serviceContext: String,
                        val serviceIp: String,
                        val servicePort: String)
