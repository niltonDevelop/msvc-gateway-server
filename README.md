# msvc-gateway-server

**API Gateway** reactivo (Spring Cloud Gateway). Punto de entrada único del ecosistema: enruta peticiones a los microservicios, gestiona OAuth2/JWT, circuit breakers y fallbacks.

## Stack

- Java 21 · Spring Boot 4.0.6 · Spring Cloud 2025.1.1
- Puerto: **8080**
- JWT issuer: `http://127.0.0.1:9190`

## Endpoints propios

| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/` | Estado de autenticación (nombre, authorities) |
| GET | `/tokens` | Tokens OAuth2 de la sesión web |
| POST | `/logout` | Cierre de sesión |
| * | `/fallback/products` | Fallback Resilience4j → products (503) |
| * | `/fallback/items` | Fallback Resilience4j → items (503) |
| * | `/fallback/users` | Fallback Resilience4j → users (503) |
| GET | `/oauth2/authorization/client-app` | Inicia login OAuth2 web |
| GET | `/login/oauth2/code/**` | Callback OAuth2 |

## Rutas proxy

| ID | Predicado | Destino | Descripción |
|----|-----------|---------|-------------|
| `oauth` | `/api/security/**` | `lb://oauth` | Proxy hacia OAuth (reservado) |
| `msvc-users` | `/api/users/**` | `lb://msvc-users` | Usuarios (+ circuit breaker) |
| `msvc-products` | `/api/product/**` | `lb://msvc-products` | Productos |
| `msvc-items` | `/api/items/**` | `lb://msvc-items` | Ítems (+ circuit breaker + header) |

Las rutas usan `StripPrefix=2`: el gateway recibe `/api/{servicio}/...` y reenvía el resto al microservicio.

## Importancia en el ecosistema

Es la **puerta de entrada HTTP** para clientes externos (navegador, Flutter). Unifica seguridad (JWT), enrutamiento dinámico vía Eureka (`lb://`) y resiliencia (circuit breakers).

**Dependencias:** Eureka, **oauth**, **msvc-users**, **msvc-products**, **msvc-items**.

**Consumido por:** **flutter_spring_boot** (API protegida en `:8080`).

**Orden de arranque recomendado:** 6.º, cuando Eureka, OAuth y los microservicios destino estén registrados.
