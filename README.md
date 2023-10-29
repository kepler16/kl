# KL

The purpose of `kl` is to allow using stable addresses (`.test` domains) for locally running services regardless of how or where they are running.

Having a stable domain that points to a service enables flexibility in deciding how to run different processes without the overhead of having to reconfigure other services which need to speak to each other each time the `IP:port` of a process changes.

This also allows statically defining inter-service configuration as code and committing it without worrying about the environment differences between different developers' machines. The configuration will reference the domain - which will be stable across all developer machines - but on each machine the process that that domain points to can be dynamic allowing developers to still chose how they want to run each service.

---

This tool is quite simple. It allows constructing and managing a local network topology (built on top of `docker`) for interacting with processes that can be running in docker or on the host machine.

Its responsibilities can be summarized as follows:

+ Enabled resolving `.test` domains from the host and from within docker containers
+ Proxy subdomains of the `.test` TLD to locally running processes regardless of where the traffic originated from or where the target process is running.
  + It must be able to route traffic that comes _from_ the host machine or from a docker container
  + Must be able to route traffic _to_ processes running on the host or in docker containers
+ Enable easilly managing proxy configurations

---

### Index

+ [**Installation**](#installation)
+ [**Network Topology**](./docs/network-topology.md)
+ [**Modules**](#modules)
+ [**Services**](#services)
+ [**Endpoints**](#endpoints)
+ [**Routes**](#routes)

## Installation

You can use the below script to automatically install the latest release

```bash
bash < <(curl -s https://raw.githubusercontent.com/kepler16/kl/master/install.sh)
```

Or you can get the binaries directly from the GitHub releases page and put them on your PATH.

---

If this is your first time using this tool on your machine then you will need to setup your system's DNS resolver:

```bash
eval $(kl resolver setup)
```

The you can start the docker network and proxy containers:

```bash
kl network start
# and `kl network stop` to tear it down 
```

These containers are configured to always restart, even after exiting/restarting docker.

## Network Topology

For an overview of the network topology constructed by this tool head over to the [Network Topology](./docs/network-topology.md) document.

## Modules

A module is an isolated configuration of [containers](#containers), [services](#services), [endpoints](#endpoints) and [routes](#routes). A module can also contain sub-modules which point to some remotely defined `module`. Sub-modules are resolved and merged into the root module.

A module is a directory located in `~/.config/kl/modules/` that must contain at least a file called `module.edn`. This file looks as follows:

```clj
{:modules {:module-name {:url "owner/repo"
                         :ref "optional-ref"
                         :sha "optional-sha"
                         :subdir "optional/sub/directory"}}

 :network {:services {:service-a {:endpoints {:container {:url "http://container-a"}
                                              :host {:url "http://host.docker.internal:3000"}}
                                  :default-endpoint :container}}
           :routes {:a {:host "a.test"
                        :service :service-a}}}

 ;; structurally the same as a docker-compose `volume`
 :volumes {} 

 ;; structurally the same as a docker-compose `service`
 :containers {:container-a {:image ""}}} 
```

Any defined submodules follow the same format and will be merged into the root module. When pulling modules their ref will be resolved to a commit at the point in time they are pulled and this commit will be stored in a lockfile called `module.lock.edn`. This means that if modules are changed upstream they will not affect your local configuration until you explicitly pull their updates down.

## Containers

A container is a docker-compose `service` snippet. It supports all of the same fields and will be converted into a docker-compose file when run. When running containers with `kl containers run` you will be given a choice of which containers to start. If any containers that were running are deselected, they will be stopped.

## Services

A service is a stable container around a set of [endpoints](#endpoints). A service is configured with a `:default-endpoint` to which all traffic will be routed to by default. Services are generally referenced to by [routes](#routes). This configuration allows swapping which endpoint a service is routing to without having to reconfigure individual [routes](#routes).

## Endpoints

An endpoint is defined as part of a [service](#services) and generally represents some process running somewhere that is reachable over HTTP. This can be a container, a process on the host machine or some remote service in the cloud.

## Routes

A route is an HTTP routing rule made up of a combination of `host` and `prefix`. A route points to a [service](#services) and will route to whichever `default-endpoint` is configured on the service. A route may also specify a specific [endpoint](#endpoint) on the service to route through which would override the default endpoint configured on the service.
