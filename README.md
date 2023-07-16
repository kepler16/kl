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

## Installation

You can use the below script to automatically install the latest release

```bash
bash < <(curl -s https://raw.githubusercontent.com/kepler16/kl/master/install.sh)
```

Or you can get the binaries directly from the GitHub releases page and put them on your PATH.

## Quick Start

If this is your first time using this tool on your machine then you will need to setup your system DNS resolver:

```bash
eval $(kl resolver setup)
```

The you can start the docker network and proxy containers:

```bash
kl network start
# and `kl network stop` to tear it down 
```

Proxy configurations can be managed with the `kl proxy` subcommands `create, delete, list, enable, disable`. For example, to add your first configuration run:

```bash
kl proxy create --name example --domain example.test --url ':9090'
```

This will now be routing `example.test` to port `9090` on your host machine. Specifying only the port (`:9999`) will use the default host). You can always secify the full url: `--url=http://host.docker.internal:9999`.

Configurations can be deleted, disabled and re-enabled by referencing them by name:

```bash
kl proxy disable example,another-example
```

## Network Topology

We make use of three main networking components:

+ A Traefik Proxy docker container (Bound to :80) configured to proxy `.test` subdomains to either containers or processes on the host machine
+ A dnsmasq container which handles DNS from the host machine (Bound to :53) and resolves to `127.0.0.1`
+ A dnsmasq container which handles DNS requests from docker containers and resolves to the Traefik proxy

All three components are running inside a Docker network and have predefined, static IP addresses. It's necessary to have two separate dnsmasq containers as the proxy is identified differently from the host vs from inside the docker network. On mac, the host machine will need to configure `/etc/resolver/test` to point to `127.0.0.1` so that the dnsmasq container can handle DNS requests for `.test` domains. There is a convenience `resolver setup` cli command to help automate this.

Which this topology, the following properties are observed:

+ All `*.test` DNS requests made from the host machine are routed to `127.0.0.1`
+ All `*.test` DNS requests made from within the docker network are routed to `172.16.238.4` which is the IP address of the proxy inside the network

> Note: All docker containers that need to be able to resolve `.test` domains must have their DNS server (use the flag `--dns`) set to the IP of the internal dnsmasq container

```mermaid
flowchart TD
  subgraph host[Host]
    proc3

    subgraph docker[Docker Network]
      proc1
      proc2

      subgraph dns
       de[Dnsmasq External]
       di[Dnsmasq Internal]
      end

      proxy[Traefik Proxy]

      proc1 -. proc2.test .-> di
      proc2 -. proc3.test .-> di

      di -. dns resolution .-> proxy
    end
  end


  proc3 -. proc1.test .-> de -. dns resolution .-> proxy

  proxy --proxies to--> proc1 & proc2 & proc3
```

## IPs

+ Internal DNSMasQ - `172.5.0.100`
+ Proxy IP - `172.5.0.101`

## Proxy Configuration

The Traefik Proxy is configured with the `docker` and `file` providers. The `file` provider is configured to watch the mounted directory `~/.config/kl/proxy` which can be used by users and tools to configure arbitrary proxy endpoints. It is recommended to read up on [Traefik Proxy](https://doc.traefik.io/traefik/) to get an overall understanding of how the proxy works and how to configure it. For the lazy, below is a tl;dr of the most common type of configuration.

If you are wanting to route traffic to docker containers then you can use labels on the docker container:

```yml
networks:
  kl:
    external: true

services:
  example:
    image: tutum/hello-world
    dns: 172.5.0.100
    networks:
    - kl
    expose: ["80"]
    labels:
    - traefik.http.routers.example.rule=Host(`example.test`)
    # - traefik.http.services.proxy.loadbalancer.example.port=80 # Use this label if there are no `expose`d ports (or if there are more than 1)
```

Traefik will automatically discover the container and configure itself to proxy `example.test` to port `80` on the container. To specify a different port you can use `traefik.http.services.example.loadbalancer.server.port=1234`.

---

To manually configure Traefik to proxy requests to processes running on the host machine, you will need to use the `file` provider. Create or reuse a file in your `~/.config/kl/proxy` directory and add the following config:

```yml
# ~/.config/kl/proxy/example.yml
http:
  routers:
    test:
      rule: Host(`example.test`)
      service: example

  services:
    example:
      loadBalancer:
        servers:
        - url: http://host.docker.internal:8080
```

Substituting all occurrences of `example` with the service name (i.e. `example`) and `8080` with whatever port the process is bound to on your host.

> Note that because the Traefik proxy is running in the docker network, you need to refer to your host machine as `host.docker.internal` instead of the normal `localhost` or `127.0.0.1`.

Traefik will automatically detect the file change and reconfigure itself.

You can access `http://proxy.test` to see all configured routing rules.

---

It is recommended however to use the `kl proxy` subcommands for managing host machine proxy configurations. It removes the overhead of having to edit this file manually and has a mechanism for disabling/enabling proxy configurations without having to keep deleting/readding them.
