# Modelo de amenazas — CECAPI (una página)

**Base:** commit `8019dd1` · 28 sep 2026 · Agente 3.

CECAPI es una app Android local para personas ciegas o con muy poca visión, manejada por voz. Hoy: solo cuentas demo, sin datos reales, IA apagada y sin conectar. El modelo describe el riesgo actual y el que aparece al usar datos reales o conectar la IA.

## Qué protegemos (activos)

| Activo | Dónde vive | Sensibilidad |
|--------|-----------|--------------|
| Hashes de contraseña | `cecapi.db` (tabla `usuarios`) | Alta (SHA-256 sin sal, S-01) |
| Fotos de documentos y del entorno | `filesDir/` | Alta (posibles datos personales) |
| Historial de IA / chats / resultados | `cecapi.db` | Media-Alta |
| Notificaciones de otras apps | Solo en memoria (`NotificationInbox`) | Alta mientras el app vive |
| Nombre y "nombre preferido" | `cecapi.db` y DataStore | Media |
| El hecho de tener discapacidad visual | Implícito en el uso del app | Alta (dato sensible; lo trata el Agente 5) |
| Voz (audio del micrófono) | Reconocedor del SO (Google) | Alta al dictar la contraseña (S-05) |

## Quién podría atacar y cómo

| Actor | Capacidad | Ruta principal | Hallazgos |
|-------|-----------|----------------|-----------|
| **Alguien con el teléfono en la mano** | Acceso físico, app abierta o desbloqueada | Adelantar el reloj para saltar el bloqueo; entrar con cuentas demo públicas | S-12, S-02 |
| **Acceso físico + PC (adb)** | `adb backup`, lectura del almacenamiento | Copia de seguridad con la DB (hashes) y las fotos | **S-03**, S-04 |
| **Otra app en el mismo teléfono** | App maliciosa instalada | Llamar componentes exportados | S-08 (mitigado: los 3 son legítimos) |
| **Atacante en la red** | Intercepta tráfico | Llamada al backend de IA sin token/pinning | S-06 (no explotable hoy: IA apagada) |
| **Proveedor de reconocimiento de voz (Google)** | Recibe el audio del SO | La contraseña dictada se transcribe en la nube | **S-05** |
| **Proveedor de IA** | Recibe las frases enviadas | Solo si se enciende y conecta la IA | S-06 + términos (Agente 5) |
| **Quien decompila el APK** | Ingeniería inversa | Release sin ofuscar; credenciales demo en el código | S-07, S-02 |
| **Robo o extravío del teléfono** | Todo lo anterior combinado | DB + fotos sin cifrar, saltables por respaldo | S-03, S-04, S-01 |

## Superficies de entrada

- **Micrófono / reconocedor del SO:** canal siempre-en-la-nube por defecto; crítico para la contraseña (S-05).
- **Componentes exportados:** `MainActivity`, `CecapiNotificationListener` (protegido por permiso del SO), `AssistantWidgetProvider`. Sin exposición explotable hoy (S-08).
- **Copia de seguridad de Android:** vía de salida de datos hoy activa (S-03).
- **Backend de IA:** inactivo; superficie futura (S-06).
- **Auto-registro:** cualquiera crea cuenta (S-15).

## Lo que reduce el riesgo hoy (verificado)

- Interruptor de IA apaga de verdad el envío; por defecto apagado (S-13).
- Notificaciones de otras apps: solo en memoria, no se persisten ni entran en el respaldo.
- Selector de fotos sin permiso de galería, copia a almacenamiento privado (S-11).
- Log de login sin usuario ni contraseña (S-14).
- Fotos y base en almacenamiento privado del app (bueno, pero anulado por el respaldo, S-03).

## Prioridad defensiva

1. Cerrar la salida por respaldo (**S-03**) — barato y de alto impacto.
2. Proteger la contraseña dictada (**S-05**) — opción de teclado + offline + aviso.
3. Hashing fuerte antes de datos reales (**S-01**).
4. Borrado realmente permanente (**S-09/S-10**), cifrado en reposo (**S-04**) y ofuscación (**S-07**) según decida Pp.
5. Antes de conectar la IA: token, límite por dispositivo y revisión del servidor (**S-06**, pendiente).
