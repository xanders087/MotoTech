# Integración de Google Maps y Geolocalización GPS en Sección de Talleres MotoTech

Plan de implementación para conectar la sección de talleres mecánicos con Google Maps y el sensor GPS del dispositivo Android, permitiendo a los motociclistas localizar talleres en tiempo real, calcular distancias y navegar con un solo toque.

## User Review & Critical Decisions

> [!IMPORTANT]
> **Decisiones confirmadas por el usuario:**
> 1. **Modo dual de interacción con Google Maps:** Calcular la distancia en kilómetros dentro de la app con el GPS del dispositivo y permitir abrir Google Maps para navegación interactiva y visualización completa.
> 2. **Criterio de búsqueda en Google Maps:** Búsqueda abierta de talleres mecánicos reportados en la zona (`geo:0,0?q=taller+mecanico+motos` / `https://www.google.com/maps/search/?api=1&query=taller+mecanico+motos`), además de los talleres certificados de la red MotoTech con descuento y reserva.

---

## 1. Overview & Core Concept

- **Qué hace:** Convierte la sección de talleres en un radar geolocalizado en tiempo real. La app solicita permisos de ubicación (`ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`), obtiene las coordenadas del usuario, calcula la distancia dinámica en km a los talleres recomendados, y ofrece dos botones de acción clave:
  1. **"Explorar talleres en Google Maps":** Abre la app de Google Maps mostrando todos los talleres mecánicos de motocicletas alrededor de la ubicación actual.
  2. **"Cómo llegar" en cada tarjeta de taller:** Inicia una ruta paso a paso (turn-by-turn navigation) directa hacia ese taller exacto en Google Maps.
- **Audiencia objetivo:** Motociclistas particulares y flotas comerciales B2B que necesitan auxilio mecánico urgente o mantenimiento preventivo en cualquier lugar de la ciudad o ruta.
- **Valor clave:** Agilidad en ruta sin fricción: si el motociclista se queda varado o requiere servicio, con 1 tap encuentra mecánicos cercanos en Maps o se dirige a su taller de confianza con indicaciones en vivo.

---

## 2. User Experience & Visual Design

- **Flujo de Usuario:**
  1. El usuario entra en la pestaña **"Talleres"**.
  2. Ve un **Banner de Telemetría GPS / Radar**:
     - Muestra estado del GPS: *Localizando...* -> *Ubicación detectada (lat, lon)*.
     - Indicador de radio de cobertura (ej. *Radio activo: 10 km*).
     - Botón principal de acción rápida: **"Buscar talleres en Google Maps"** con ícono de mapa y acento llamativo de cabina.
  3. **Tarjetas de Talleres:**
     - Muestran la distancia real calculada (ej. `1.8 km • ~5 min en moto`).
     - Botón **"Cómo llegar (Maps)"** con ícono de navegación para abrir la ruta en Google Maps.
     - Botón **"Reservar Cita"** para agendar con descuento de convenio MotoTech.
  4. **Manejo de Permisos:** Si el usuario no ha otorgado el permiso de ubicación, se muestra un banner amigable solicitando el permiso sin bloquear la visualización con coordenadas de referencia por defecto.

- **Diseño Visual e Identidad:**
  - Estilo de cabina oscura de alta precisión (*Dark Cockpit* `#0C0E14`, bordes neón `#1F2430`, acentos en cian telemétrico `#38BDF8` y verde rutinario `#4ADE80`).
  - Botones táctiles de al menos 48dp de altura para fácil uso con guantes de moto o en movimiento.

---

## 3. Key Product Decisions & Trade-Offs

- **Decisión 1: Integración mediante Android Intents vs. Maps SDK nativo pesado**
  - *Enfoque elegido:* Utilizar Intents directos de Android (`Intent.ACTION_VIEW` con URI `geo:` y `https://www.google.com/maps/dir/?api=1...`) combinado con el servicio de localización de Android para cálculo local de distancias.
  - *Por qué:* No requiere API Key de Maps Platform con cuotas ni costos mensuales al usuario, abre la aplicación nativa de Google Maps instalada en el dispositivo (que ofrece navegación paso a paso con tráfico en tiempo real y comandos por voz), y mantiene el APK ligero y rápido de compilar.
  - *Alternativas consideradas:* Google Maps SDK embebido en Compose (requiere Maps API Key paga, no permite navegación paso a paso fluida como la app nativa y aumenta el peso del binario).

- **Decisión 2: Búsqueda flexible de talleres**
  - *Enfoque elegido:* Botón maestro de búsqueda amplia en Google Maps ("taller mecánico motos cerca de mí") junto con las tarjetas específicas de la red certificada de MotoTech.
  - *Por qué:* Resuelve tanto la necesidad de ver cualquier taller de emergencia en la calle reportado en Maps como la de acudir a un taller aliado verificado con descuento de garantía.

---

## 4. Technical Architecture & Data Strategy

### Diagrama de Flujo y Componentes

```
┌──────────────────────────────────────────────────────────────┐
│                    WorkshopsScreen (UI)                      │
│                                                              │
│  ┌────────────────────────┐      ┌─────────────────────────┐ │
│  │   GPS Telemetry Radar  │      │  "Buscar en Google      │ │
│  │   • Estado GPS         │ ───> │   Maps" (Intent Global) │ │
│  │   • Coordenadas en vivo│      └─────────────────────────┘ │
│  └───────────┬────────────┘                                  │
│              │                                               │
│              ▼                                               │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │  Lista de Talleres con Distancia Calculada (Haversine)  │ │
│  │  • Taller MotoTech Express (1.8 km)                     │ │
│  │    [Cómo llegar en Maps] ────> Abre Ruta Navegación GPS │ │
│  │    [Reservar Cita]       ────> Abre Modal de Reserva    │ │
│  └─────────────────────────────────────────────────────────┘ │
└──────────────────────────────┬───────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────┐
│                 Location & Intent Helpers                    │
│  • LocationManager / FusedLocationProviderClient             │
│  • rememberLauncherForActivityResult (ACCESS_FINE_LOCATION)  │
│  • Intent(Intent.ACTION_VIEW, Uri.parse("geo:..."))          │
└──────────────────────────────────────────────────────────────┘
```

### Componentes y Cambios Técnicos Específicos:
1. **`AndroidManifest.xml`**:
   - Agregar `<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />`
   - Agregar `<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />`
2. **`WorkshopsScreen.kt`**:
   - Integrar `rememberLauncherForActivityResult` para solicitar permisos de ubicación en Compose.
   - Obtener la posición del usuario usando los servicios de localización de Android y calcular la distancia esférica real (fórmula de Haversine) a cada taller.
   - Implementar función helper `openGoogleMapsSearch(context, lat, lon)`: lanza intent `geo:lat,lon?q=taller+mecanico+motos` con fallback web a Google Maps.
   - Implementar función helper `openGoogleMapsNavigation(context, lat, lon, label)`: lanza intent de navegación paso a paso a ese destino en Google Maps.
   - Actualizar el encabezado y las tarjetas de talleres con los nuevos botones e indicadores visuales de navegación directa.
