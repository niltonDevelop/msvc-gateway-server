# Distributed Tracing en msvc-gateway-server

> **Guía completa:** [docs/ZIPKIN.md](../../docs/ZIPKIN.md) — referencia senior de Zipkin para todo el ecosistema.

[Micrometer Tracing](https://docs.micrometer.io/tracing/reference/) + **Brave** + **Zipkin** en gateway reactivo (WebFlux).

| Componente | Span automático |
|------------|-----------------|
| Peticiones HTTP entrantes (`/api/**`) | Sí (span raíz desde el cliente) |
| Spring Cloud Gateway → microservicios (`lb://`) | Sí (propagación W3C/B3) |
| Resilience4j Circuit Breaker | Sí |
| Logs | `[msvc-gateway-server,traceId,spanId]` |

## Flujo completo del ecosistema

```
Cliente → gateway → oauth / users / items / products
          span raíz   └── spans hijos (mismo traceId) ──┘
```

Ejemplo login: `POST /api/auth/login` → oauth → msvc-users  
Ejemplo catálogo: `GET /api/items/items` → msvc-items → msvc-products

## Zipkin (local)

Desde la raíz `SpringCloud/`:

```bash
docker compose up -d
```

En [http://localhost:9411](http://localhost:9411) verás la traza completa con todos los hops.

## Referencias

- [Spring Boot — Tracing](https://docs.spring.io/spring-boot/reference/actuator/tracing.html)
- [Micrometer Tracing](https://docs.micrometer.io/tracing/reference/)
