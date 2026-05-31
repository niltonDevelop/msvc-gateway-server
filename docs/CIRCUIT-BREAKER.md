# Circuit Breaker en msvc-gateway-server

Casos de prueba para validar el **Circuit Breaker** de **Resilience4j** en el **API Gateway** (Spring Cloud Gateway WebFlux).

---

## Resumen

El gateway expone rutas hacia microservicios con filtro `CircuitBreaker` y fallback local:

| Ruta gateway | Instancia CB | Fallback |
|---|---|---|
| `GET/POST /api/v1/product/**` | `products` | `forward:/fallback/products` → **503** JSON |
| `GET /api/v1/items/**` | `items` | `forward:/fallback/items` → **503** JSON |

Configuración: `src/main/resources/application.yml`  
Puerto gateway: **8090**

> **Importante:** `msvc-items` tiene su propio circuit breaker hacia `msvc-products`. Si items responde **200** con item genérico, el gateway **no** activa su fallback (solo reenvía la respuesta). Los casos de items en este documento distinguen **fallback del gateway** vs **fallback interno de items**.

---

## Prerrequisitos

1. **Eureka** en `http://localhost:8761`
2. **msvc-gateway-server** en puerto **8090**
3. **msvc-products** registrado en Eureka
4. **msvc-items** registrado en Eureka
5. Al menos un producto válido en BD (por ejemplo id **1**)

Para resetear el estado del circuit breaker entre sesiones, **reinicia el gateway**.

Orden de arranque recomendado: Eureka → products → items → gateway.

---

## Rutas y transformación

| Petición al gateway | Tras `StripPrefix=2` | Microservicio |
|---|---|---|
| `GET /api/v1/product/1` | `GET /product/1` | msvc-products |
| `GET /api/v1/items/1` | `GET /items/1` | msvc-items |

Base URL de pruebas: `http://localhost:8090`

---

## IDs especiales en msvc-products

| ID | Comportamiento upstream |
|---|---|
| **1** (u otro existente) | Respuesta normal **200** |
| **10** | **500** `{"error":"Producto no encontrado"}` |
| **7** | `sleep(5s)` → respuesta lenta |
| **99999** | **404** si no existe en BD |

---

## Casos de prueba — ruta `products`

### CP-GW-P01 — CLOSED: respuesta exitosa

| Campo | Valor |
|---|---|
| **Instancia CB** | `products` — CLOSED |
| **Petición** | `GET http://localhost:8090/api/v1/product/1` |
| **HTTP esperado** | `200 OK` |
| **Body esperado** | JSON del producto real (desde BD) |
| **Fallback gateway** | No |

```bash
curl -i http://localhost:8090/api/v1/product/1
```

---

### CP-GW-P02 — CLOSED: 404 sin fallback del gateway

| Campo | Valor |
|---|---|
| **Instancia CB** | `products` — CLOSED |
| **Petición** | `GET http://localhost:8090/api/v1/product/99999` |
| **HTTP esperado** | `404 Not Found` |
| **Fallback gateway** | No (404 no está en `statusCodes`) |

```bash
curl -i http://localhost:8090/api/v1/product/99999
```

---

### CP-GW-P03 — CLOSED: error 500 → fallback del gateway

| Campo | Valor |
|---|---|
| **Instancia CB** | `products` — CLOSED |
| **Petición** | `GET http://localhost:8090/api/v1/product/10` |
| **HTTP esperado** | `503 Service Unavailable` |
| **Body esperado** | `{"error":"Servicio products no disponible temporalmente","service":"msvc-products"}` |
| **Motivo** | products responde **500** → `statusCodes` incluye `INTERNAL_SERVER_ERROR` → fallback |

```bash
curl -i http://localhost:8090/api/v1/product/10
```

---

### CP-GW-P04 — CLOSED: timeout → fallback del gateway

| Campo | Valor |
|---|---|
| **Instancia CB** | `products` — CLOSED |
| **Petición** | `GET http://localhost:8090/api/v1/product/7` |
| **HTTP esperado** | `503 Service Unavailable` |
| **Tiempo aprox.** | ~3 s (time limiter; products duerme 5 s) |
| **Body esperado** | JSON fallback gateway (`service: msvc-products`) |

```bash
curl -i -w "\nTiempo total: %{time_total}s\n" http://localhost:8090/api/v1/product/7
```

---

### CP-GW-P05 — OPEN: apertura tras fallos acumulados

| Campo | Valor |
|---|---|
| **Instancia CB** | `products` — CLOSED → OPEN |
| **Petición** | `GET http://localhost:8090/api/v1/product/10` repetido **6 veces** |
| **HTTP esperado** | `503` en todas |
| **Diferencia clave** | Tras OPEN, respuestas más rápidas (no llama a products) |

```bash
for i in {1..6}; do
  echo "--- Intento $i ---"
  curl -s -o /dev/null -w "HTTP %{http_code} en %{time_total}s\n" \
    http://localhost:8090/api/v1/product/10
done
```

**Criterio de éxito:** a partir del intento 6 el tiempo baja respecto a los primeros (circuito OPEN).

---

### CP-GW-P06 — HALF_OPEN → CLOSED: recuperación

| Campo | Valor |
|---|---|
| **Precondición** | CP-GW-P05 completado (circuito OPEN) |
| **Paso 1** | Esperar **10 s** (`waitDurationInOpenState=10s`) |
| **Paso 2** | `GET http://localhost:8090/api/v1/product/1` |
| **HTTP esperado** | `200 OK` con producto real |
| **Resultado** | CB vuelve a **CLOSED** |

```bash
echo "Esperando 10s para HALF_OPEN..."
sleep 10
curl -i http://localhost:8090/api/v1/product/1
```

---

### CP-GW-P07 — OPEN: circuito abierto (id válido)

| Campo | Valor |
|---|---|
| **Precondición** | CP-GW-P05 completado, sin esperar recuperación |
| **Petición** | `GET http://localhost:8090/api/v1/product/1` |
| **HTTP esperado** | `503` (fallback gateway, aunque id=1 exista en BD) |

```bash
curl -i http://localhost:8090/api/v1/product/1
```

---

### CP-GW-P08 — Servicio caído: products no disponible

| Campo | Valor |
|---|---|
| **Precondición** | Detener **msvc-products** |
| **Petición** | `GET http://localhost:8090/api/v1/product/1` |
| **HTTP esperado** | `503` con JSON fallback gateway |

```bash
curl -i http://localhost:8090/api/v1/product/1
```

---

## Casos de prueba — ruta `items`

### CP-GW-I01 — CLOSED: respuesta exitosa vía gateway

| Campo | Valor |
|---|---|
| **Instancia CB** | `items` — CLOSED |
| **Petición** | `GET http://localhost:8090/api/v1/items/1` |
| **HTTP esperado** | `200 OK` |
| **Body esperado** | Item real (`category` ≠ `"fallback"`) |
| **Fallback gateway** | No |

```bash
curl -i http://localhost:8090/api/v1/items/1
```

---

### CP-GW-I02 — Fallback interno de items (no es fallback del gateway)

| Campo | Valor |
|---|---|
| **Instancia CB gateway** | `items` — CLOSED |
| **Petición** | `GET http://localhost:8090/api/v1/items/10` |
| **HTTP esperado** | `200 OK` |
| **Body esperado** | Item genérico de **msvc-items** (`"category": "fallback"`) |
| **Nota** | items absorbe el 500 de products y responde 200; el gateway **no** entra en fallback |

```bash
curl -i http://localhost:8090/api/v1/items/10
```

---

### CP-GW-I03 — Fallback del gateway: items caído

| Campo | Valor |
|---|---|
| **Precondición** | Detener **msvc-items** (products puede estar UP) |
| **Petición** | `GET http://localhost:8090/api/v1/items/1` |
| **HTTP esperado** | `503 Service Unavailable` |
| **Body esperado** | `{"error":"Servicio items no disponible temporalmente","service":"msvc-items"}` |

```bash
curl -i http://localhost:8090/api/v1/items/1
```

---

### CP-GW-I04 — OPEN: apertura del CB `items` en gateway

| Campo | Valor |
|---|---|
| **Precondición** | **msvc-items** detenido |
| **Petición** | `GET http://localhost:8090/api/v1/items/1` repetido **6 veces** |
| **HTTP esperado** | `503` en todas |
| **Diferencia clave** | Tras OPEN, tiempos de respuesta más bajos |

```bash
for i in {1..6}; do
  echo "--- Intento $i ---"
  curl -s -o /dev/null -w "HTTP %{http_code} en %{time_total}s\n" \
    http://localhost:8090/api/v1/items/1
done
```

---

### CP-GW-I05 — Listado items con products caído

| Campo | Valor |
|---|---|
| **Precondición** | Detener **msvc-products** |
| **Petición** | `GET http://localhost:8090/api/v1/items` |
| **HTTP esperado** | `502`, `503` o `504` (según cadena de fallos) |
| **Nota** | `findAll` en items **no** devuelve item genérico; puede propagar error hasta el gateway |

```bash
curl -i http://localhost:8090/api/v1/items
```

---

## Matriz resumen

| Caso | Endpoint gateway | Condición | HTTP | ¿Fallback gateway? |
|---|---|---|---|---|
| CP-GW-P01 | `/api/v1/product/1` | Normal | 200 | No |
| CP-GW-P02 | `/api/v1/product/99999` | 404 upstream | 404 | No |
| CP-GW-P03 | `/api/v1/product/10` | 500 upstream | 503 | Sí |
| CP-GW-P04 | `/api/v1/product/7` | Timeout ~3s | 503 | Sí |
| CP-GW-P05 | `/api/v1/product/10` ×6 | Acumular fallos | 503 | Sí |
| CP-GW-P06 | `/api/v1/product/1` tras 10s | Recuperación | 200 | No |
| CP-GW-P07 | `/api/v1/product/1` CB OPEN | Circuito abierto | 503 | Sí |
| CP-GW-P08 | `/api/v1/product/1` | products DOWN | 503 | Sí |
| CP-GW-I01 | `/api/v1/items/1` | Normal | 200 | No |
| CP-GW-I02 | `/api/v1/items/10` | CB interno items | 200 | No (fallback items) |
| CP-GW-I03 | `/api/v1/items/1` | items DOWN | 503 | Sí |
| CP-GW-I04 | `/api/v1/items/1` ×6 | items DOWN | 503 | Sí |
| CP-GW-I05 | `/api/v1/items` | products DOWN | 5xx | Depende |

---

## Orden recomendado

### Ruta products (estados CLOSED → OPEN → HALF_OPEN)

```text
CP-GW-P01 → CP-GW-P02 → CP-GW-P03 → CP-GW-P04 → CP-GW-P05 → CP-GW-P07 → CP-GW-P06
```

### Ruta items (fallback gateway vs interno)

```text
CP-GW-I01 → CP-GW-I02 → CP-GW-I03 → CP-GW-I04
```

CP-GW-P08 e CP-GW-I05 son independientes (requieren detener servicios).

---

## Checklist de verificación

- [ ] Producto real vía gateway (CP-GW-P01)
- [ ] 404 sin fallback gateway (CP-GW-P02)
- [ ] Fallback JSON gateway ante 500 en products (CP-GW-P03)
- [ ] Fallback gateway ante timeout ~3s (CP-GW-P04)
- [ ] Circuito OPEN en instancia `products` (CP-GW-P05 / CP-GW-P07)
- [ ] Recuperación tras 10 s (CP-GW-P06)
- [ ] Item real vía gateway (CP-GW-I01)
- [ ] Diferencia fallback items (200) vs fallback gateway (503) (CP-GW-I02 vs CP-GW-I03)
- [ ] Circuito OPEN en instancia `items` (CP-GW-I04)

---

## Respuesta de fallback del gateway

```json
{
  "error": "Servicio products no disponible temporalmente",
  "service": "msvc-products"
}
```

```json
{
  "error": "Servicio items no disponible temporalmente",
  "service": "msvc-items"
}
```

Siempre **503 Service Unavailable**.

---

## Configuración de referencia

```yaml
# Instancias: products | items
resilience4j.circuitbreaker.instances.<nombre>:
  slidingWindowSize: 10
  minimumNumberOfCalls: 5
  failureRateThreshold: 50
  waitDurationInOpenState: 10s
  slowCallDurationThreshold: 3s
  slowCallRateThreshold: 50

resilience4j.timelimiter.instances.<nombre>:
  timeoutDuration: 3s
  cancelRunningFuture: true
```

```text
Gateway URL    : http://localhost:8090
Eureka         : http://localhost:8761
Instancia CB products : "products"
Instancia CB items    : "items"
Timeout               : 3 segundos
Puerto gateway        : 8090
```

---

## Troubleshooting

| Síntoma | Posible causa |
|---|---|
| Siempre 404 en gateway | Eureka no resuelve el servicio; revisa registro en `http://localhost:8761` |
| CP-GW-P03 no devuelve 503 | Verifica `statusCodes` en el filtro (debe ser string separado por comas) |
| CP-GW-I02 devuelve 200 con fallback | Comportamiento esperado: CB de **msvc-items**, no del gateway |
| CB no se resetea | Reinicia **msvc-gateway-server** |
| Puerto en uso | Otro proceso en **8090**; detén instancia previa del gateway |
