# KL

This is a development tool which provides a well defined and flexible means of running and developing your technology stack locally on your machine.

This tool is mainly beneficial to technology stacks which are distributed in nature and require lots of independent modules to run and speak to each other.

KL tries to assume as little as possible about systems being run in order to make it easy to use with any technology stack so long as the stack is or can be dockerized.

---

### Index

+ [**Concepts**](#concepts)
+ [**Installation**](#installation)
+ [**Network Topology**](./docs/network-topology.md)
+ [**Modules**](#modules)
+ [**Services**](#services)
+ [**Endpoints**](#endpoints)
+ [**Routes**](#routes)

---

## Concepts

#### Networking

The core idea behind KL is to enable identifying services with stable `.test` domains and then having a flexible means of swapping out which endpoint the domain routes traffic to. This is typically how we run our services in production environments, there is no reason not to do it locally too.

One of the main benefits that we draw from this is the significant simplification of service configuration. Services can be configured statically (committed to code/configuration) with the domains of other services they need to communicate with regardless of where those services are running or what their ip:port conbinations are.

These domains are stable across all developers' machines and don't change when a developer needs to alter how or where they are running any particular service.

For an overview of the network topology constructed by kl head over to the [Network Topology](./docs/network-topology.md) document.

#### Containers

While the benefits of the stable network addresses are significant it can also become quite tedious to run ones entire tech stack locally, especially when it is comprised of multiple individual services. Tools like docker-compose help a lot in this regard but even that starts to become very unwieldy as the number of services grow and the rate at which they change increases.

Managing large docker-compose configurations and keeping them up-to-date with changes happening in upstream services does not scale well.

KL provides a mechanism of defining container configurations alongside the services they relate to (in their respective repositories) and composing them together on each developers local machine. Because the configuration lives alongside the service it is simpler to keep in sync as changes are made to the service itself. Developers' can then use kl to pull down any upstream changes to run.

---

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

These containers are configured to always restart even after exiting/restarting docker.

## Modules

A module is the primary unit of configuration in kl and is comprised of [containers](#containers), [services](#services), [endpoints](#endpoints) and [routes](#routes). A module can also contain sub-modules which should reference externally defined module configurations. Sub-modules are recursively resolved and merged into the root module.

A module is a directory located in `~/.config/kl/modules/` that must contain at least a file called `module.(edn|json|yaml|yml)`. Here is an example of a module:

```clj
{:modules {:remote-module-name {:url "owner/repo"
                                :ref "<optional-ref>"
                                :sha "<optional-sha>"
                                :subdir "<optional/sub/directory>"}}

 :network {:services {:example {:endpoints {:container {:url "http://example"}
                                            :host {:url "http://host.docker.internal:3000"}}
                                :default-endpoint :container}}
           :routes {:example-route {:host "example.test"
                                    :service :example}}}

 ;; structurally the same as a docker-compose `volume`
 :volumes {} 

 ;; structurally the same as a docker-compose container
 :containers {:container-a {:image ""}}} 
```

#### Module Resolution

Any sub-modules defined in a module will be recursively resolved and deep-merged with it's parent module. When there are module conflicts (more than one module with the same name) then they are resolved as follows:

+ If the conflict is in a child module, the parent will be used.
+ If there are no matching parent modules then the chosen module is essentially random - the first module to resolve will be used.

The module resolution is performed once and the result stored in a lockfile called `module.lock.edn`. This allows a developers local dev-setup to be unaffected by any upstream changes _by default_ until the developer decides to explicitly pull down upstream changes using `kl module update`. It is highly recommended to commit your local `module.edn` and `module.lock.edn` files to make it easy to roll back when upstream changes cause unintended effects.

Because module resolution is implemented as a full deep-merge, this allows developers to override locally any component of the fully resolved module. For example if a developer wants to swap out the container image being used for a particular container they can do so as follows:

```clj
{:modules {...}

 :containers {:example {:image "example-image:replaced-tag"}}}
```

Note how only the property being changed needed to be specified. This is assuming the container `:example` was already defined by one of the referenced sub-modules.

## Containers

A container is a docker-compose `service` snippet. It supports all of the same fields and will be converted into a docker-compose file when run. When running containers with `kl containers run` you will be given a choice of which containers to start. If any containers that were running are deselected, they will be stopped.

## Services

A service is a stable container around a set of [endpoints](#endpoints). A service is configured with a `:default-endpoint` to which all traffic will be routed to by default. Services are generally referenced to by [routes](#routes). This configuration allows swapping which endpoint a service is routing to without having to reconfigure individual [routes](#routes).

## Endpoints

An endpoint is defined as part of a [service](#services) and generally represents some process running somewhere that is reachable over HTTP. This can be a container, a process on the host machine or some remote service in the cloud.

## Routes

A route is an HTTP routing rule made up of a combination of `host` and `prefix`. A route points to a [service](#services) and will route to whichever `default-endpoint` is configured on the service. A route may also specify a specific [endpoint](#endpoint) on the service to route through which would override the default endpoint configured on the service.
