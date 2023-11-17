# KL

This is a development tool which provides a well defined and flexible means of running and developing your technology stack locally on your machine.

This tool is mainly beneficial to technology stacks which are distributed in nature and require lots of independent modules to run and speak to each other.

KL tries to assume as little as possible about systems being run in order to make it easy to use with any technology stack so long as the stack is or can be dockerized.

---

### Index

+ [**Installation**](#installation)
+ [**Initial Setup**](#initial-setup)
+ [**Network Topology**](./docs/network-topology.md)
+ [**Concepts**](#concepts)
  + [**Modules**](#modules)
  + [**Services**](#services)
  + [**Endpoints**](#endpoints)
  + [**Routes**](#routes)
+ [**Prompts**](#Prompts)

---

## Installation

#### Homebrew

```bash
brew install kepler16/tap/kl
```

#### Curl

You can use the below script to automatically install the latest release

```bash
bash < <(curl -s https://raw.githubusercontent.com/kepler16/kl/master/install.sh)
```

Or you can get the binaries directly from the GitHub releases page and put them on your PATH.

---

## Initial Setup

If this is your first time using this tool on your machine then you will need to configure your system to resolve `.test` domains to `127.0.0.1:80`. This is essential for the networking components to work. How this should be configured depends on your operating system.

#### Macos

On Macos you need to configure the native resolver to use the nameserver at `127.0.0.1:53` when resolving `.test` domains. This is done by creating a file at `/etc/resolver/test` with the contents:

```
nameserver 127.0.0.1
```

You can run the following script do this automatically:

```bash
sudo mkdir -p /etc/resolver
echo 'nameserver 127.0.0.1' | sudo tee -a /etc/resolver/test > /dev/null
```

KL runs this DNS server for you as one of the networking containers. See the [Network Topology](./docs/network-topology.md) document for more information on the networking containers.

Now you can start the docker network and proxy containers:

```bash
kl network start
```

#### Linux

Linux doesn't have a completely standard way of handling DNS and so this setup will depend a bit on your particular setup. A very common/standard DNS resolver setup on linux is `systemd-resolved` and so below is a guide on how to set get setup using this. If you don't use `systemd-resolved` then you will need to configure your system to route `.test` domains to `127.0.0.1` however is appropriate for you.

Edit your `/etc/systemd/resolved.conf` file and add the following to the `[Resolve]` section:

```toml
[Resolve]
DNS=127.0.0.1:5343
Domains=~test
```

This configures `systemd-resolved` to use the DNS server running at `127.0.0.1:5343` to resolve `.test` domains. KL runs this DNS server for you as one of the networking containers. See the [Network Topology](./docs/network-topology.md) document for more information on the networking containers.

Apply the changes by restarting the `systemd-resolved` service

```bash
sudo systemctl restart systemd-resolved
```

Now you can start the docker network and proxy containers:

```bash
kl network start
```

> [!NOTE]  
> On linux the host dns network container's port defaults to `5343`. If you would like to change this you can start the networking components with a different port by running `kl network start --host-dns-port=5343`

---

## Concepts

#### Networking

The core idea behind KL is to enable identifying services with stable `.test` domains and then having a flexible means of swapping out which endpoint the domain routes traffic to. This is typically how we run our services in production environments, there is no reason not to do it locally too.

One of the main benefits that we draw from this is the significant simplification of service configuration. Services can be configured statically (committed to code/configuration) with the domains of other services they need to communicate with regardless of where those services are running or what their ip:port combinations are.

These domains are stable across all developers' machines and don't change when a developer needs to alter how or where they are running any particular service.

For an overview of the network topology constructed by kl head over to the [Network Topology](./docs/network-topology.md) document.

#### Containers

While the benefits of the stable network addresses are significant it can also become quite tedious to run ones entire tech stack locally, especially when it is comprised of multiple individual services. Tools like docker-compose help a lot in this regard but even that starts to become very unwieldy as the number of services grow and the rate at which they change increases.

Managing large docker-compose configurations and keeping them up-to-date with changes happening in upstream services does not scale well.

KL provides a mechanism of defining container configurations alongside the services they relate to (in their respective repositories) and composing them together on each developers local machine. Because the configuration lives alongside the service it is simpler to keep in sync as changes are made to the service itself. Developers' can then use kl to pull down any upstream changes to run.

---

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

---

## Prompts

KL has built-in support for [fzf](https://github.com/junegunn/fzf) and [gum](https://github.com/charmbracelet/gum) for prompt interfaces and selections. If these programs are installed and on your `PATH` then they will automatically be used. Alternatively kl will fallback to using a native prompt implementation if neither gum nor fzf can be found.

For a better prompt experience it is highly recommended to have these programs installed on your PATH.

## FAQ

### Why does the host dnsmasq container bind a different port on Macos compared to Linux?

On macos there is no way to specify the port to use when configuring the system resolver via the `/etc/resolver/test` path. Therefore we need to use port `53`.

On Linux there is typically already some dns components binding port `53` and so we default to using a different, non-standard port - `5343`.
