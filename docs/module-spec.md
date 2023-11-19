# Module Spec

Field | Type | Description
-|-|-
include | string[]? | A list of file paths relative to the module file that should be downloaded alongside the module
modules | map<string, [ModuleRef](#ModuleRef)>? | A list of file paths relative to the module that should be downloaded alongside the module
network | [NetworkConfig](#NetworkConfig)? | Network config for the module
networks | map? | A docker-compose [`networks`](https://docs.docker.com/compose/compose-file/06-networks/) snippet.
volumes | map? | A docker-compose [`volumes`](https://docs.docker.com/compose/compose-file/07-volumes/) snippet.
containers | map? | A docker-compose [`services`](https://docs.docker.com/compose/compose-file/05-services/) snippet.

## `ModuleRef`

Field | Type | Description
-|-|-
url | string | The github repo identifier of a remote module in the form `<owner>/<repo>`. Currently this is the only supported value but in future this will be expanded to support additional remote sources.
ref | string? | A git ref that the remote module should be resolved against. Defaults to `master`
sha | string? | An exact sha that should be used instead of dynamically resolving it from a ref. Exclusive with ref.
subdir | string? | A directory path within the remote repository that the `module` config file should be resolved from. Defaults to `.kl`

## `NetworkConfig`

Field | Type | Description
-|-|-
services | map<string, [Service](#Service)>? | A set of services defined by the module
routes | map<string, [Route](#Route)>? | A set of routes defined by the module

## `Service`

Field | Type | Description
-|-|-
endpoints | map<string, [Endpoint](#Endpoint)> | A set of services defined by the module
default-endpoint | string | The name of one of the defined endpoints to which traffic should be routed to by default

## `Endpoint`

Field | Type | Description
-|-|-
url | string | The URL of some HTTP service running somewhere reachable by the proxy container

## `Route`

Field | Type | Description
-|-|-
host | string | The `.test` domain that should match for this route to be used
path-prefix | string? | The subpath of the request that should match for this route to be used. Defaults to `/`
service | string | The name of a defined service that traffic should be routed to when this route matches a request
endpoint | string? | The endpoint within the defined service that should be used. Defaults to the `default-endpoint` on the service
