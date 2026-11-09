# build-ea-model.ps1
# Construye el modelo UML del TFG en Enterprise Architect mediante su interfaz de automatización.
param(
  [string]$UmlDir = $PSScriptRoot,
  [string]$ModelName = "TFG-HMU-Modelo"
)
$ErrorActionPreference = "Stop"
$log = Join-Path $UmlDir "build-ea-model.log"
if (Test-Path $log) { Remove-Item $log -Force }
function Log($m) {
  $line = "[{0}] {1}" -f (Get-Date -Format "HH:mm:ss"), $m
  for ($k = 0; $k -lt 20; $k++) { try { Add-Content -Path $log -Value $line -Encoding UTF8; break } catch { Start-Sleep -Milliseconds 250 } }
  Write-Host $line
}
function Fail($m) { Log ("ERROR: " + $m); Log $_.ScriptStackTrace; exit 1 }

try {
  # --- Conexión con EA -------------------------------------------------------
  $rep = $null
  try {
    $app = [Runtime.InteropServices.Marshal]::GetActiveObject("EA.App")
    $rep = $app.Repository
    Log "Conectado a la instancia de EA en ejecución"
  } catch {
    Log "No hay instancia visible de EA; se crea una nueva (New-Object EA.Repository)"
    $rep = New-Object -ComObject EA.Repository
  }
  $ver = [int]$rep.LibraryVersion
  Log ("LibraryVersion = " + $ver)
  $ext = "eapx"; $ctype = 1
  if ($ver -ge 1600) { $ext = "qea"; $ctype = 2 }
  $modelPath = Join-Path $UmlDir ($ModelName + "." + $ext)
  try { $rep.CloseFile() } catch {}
  if (Test-Path $modelPath) { Remove-Item $modelPath -Force; Log "Modelo previo eliminado" }
  $ok = $false
  if (-not (Test-Path $modelPath)) {
    $eaExe = (Get-Process EA -ErrorAction SilentlyContinue | Select-Object -First 1).Path
    if (-not $eaExe) { $eaExe = "C:\Program Files (x86)\Sparx Systems\EA Trial\EA.exe" }
    $base = Join-Path (Split-Path $eaExe) ("EABase." + $ext)
    Log ("Copiando modelo base: " + $base)
    Copy-Item $base $modelPath -Force
  }
  if (-not $rep.OpenFile($modelPath)) { throw "No se pudo abrir $modelPath" }
  Log ("Modelo abierto: " + $modelPath)
  $rep.EnableUIUpdates = $false
  $proj = $rep.GetProjectInterface()
  $pngDir = Join-Path $UmlDir "png"
  if (-not (Test-Path $pngDir)) { New-Item -ItemType Directory -Path $pngDir | Out-Null }

  # --- Helpers ---------------------------------------------------------------
  function New-Pkg($parent, $name) { $p = $parent.Packages.AddNew($name, "Package"); $p.Update() | Out-Null; return $p }
  function New-El($pkg, $name, $type, $stereo, $notes) {
    $e = $pkg.Elements.AddNew($name, $type)
    if ($stereo) { $e.Stereotype = $stereo }
    if ($notes) { $e.Notes = $notes }
    $e.Update() | Out-Null
    return $e
  }
  function New-Dgm($pkg, $name, $type, $notes) {
    $d = $pkg.Diagrams.AddNew($name, $type)
    if ($notes) { $d.Notes = $notes }
    $d.Update() | Out-Null
    try { $rep.Execute("UPDATE t_diagram SET ShowForeign=0 WHERE Diagram_ID=" + $d.DiagramID) } catch {}
    return $d
  }
  function Hide-Links($dgm, $ids) {
    foreach ($cid in $ids) {
      try { $dl = $dgm.DiagramLinks.AddNew("", ""); $dl.ConnectorID = $cid; $dl.IsHidden = $true; $dl.Update() | Out-Null } catch { Log ("Hide-Links: " + $_.Exception.Message) }
    }
  }
  function Add-Obj($dgm, $el, $l, $t, $w, $h) {
    $r = $l + $w; $b = $t + $h
    $o = $dgm.DiagramObjects.AddNew("l=$l;r=$r;t=$t;b=$b;", "")
    $o.ElementID = $el.ElementID
    $o.Update() | Out-Null
    return $o
  }
  function New-Con($src, $dst, $type, $stereo, $name) {
    $c = $src.Connectors.AddNew($name, $type)
    $c.SupplierID = $dst.ElementID
    if ($stereo) { $c.Stereotype = $stereo }
    $c.Update() | Out-Null
    return $c
  }
  function Export-Png($dgm, $file) {
    $f = Join-Path $pngDir ($file + ".png")
    try { $rep.ReloadDiagram($dgm.DiagramID) } catch {}
    $r = $proj.PutDiagramImageToFile($dgm.DiagramGUID, $f, 1)
    Log ("PNG " + $file + " -> " + $r)
  }
  function Set-DgmStyle($dgm, $overrides) {
    try {
      $pd = $rep.SQLQuery("SELECT PDATA FROM t_diagram WHERE Diagram_ID=" + $dgm.DiagramID)
      $s = ""
      if ($pd -match "<PDATA>(.*?)</PDATA>") { $s = $matches[1] }
      if (-not $s) { $s = "HideRel=0;ShowTags=0;ShowReqs=0;ShowCons=0;OpParams=1;ShowSN=0;ScalePI=0;PPgs.cx=1;PPgs.cy=1;PSize=9;ShowIcons=1;SuppCN=0;HideProps=0;HideParents=0;UseAlias=0;HideAtts=0;HideOps=0;HideStereo=0;HideEStereo=0;ShowRec=1;ShowRes=0;ShowShape=1;FormName=;" }
      foreach ($k in $overrides.Keys) {
        $v = $overrides[$k]
        if ($s -match ($k + "=")) { $s = $s -replace ($k + "=[^;]*"), ($k + "=" + $v) } else { $s = $s + "$k=$v;" }
      }
      $rep.Execute("UPDATE t_diagram SET PDATA='" + $s + "' WHERE Diagram_ID=" + $dgm.DiagramID)
    } catch { Log ("Set-DgmStyle: " + $_.Exception.Message) }
  }

  # --- Raíz ------------------------------------------------------------------
  if ($rep.Models.Count -eq 0) { $root = $rep.Models.AddNew("Modelo", ""); $root.Update() | Out-Null; $rep.Models.Refresh() }
  $root = $rep.Models.GetAt(0)
  $root.Name = 'Sistema de diagramación de servicios y gestión del ausentismo (HMU)'
  $root.Update() | Out-Null
  # limpiar paquetes por defecto del modelo base
  for ($i = $root.Packages.Count - 1; $i -ge 0; $i--) { $root.Packages.Delete($i) }
  $root.Packages.Refresh()


  # ===== 1. Requisitos ========================================================
  Log "Paquete Requisitos"
  $pkgReq = New-Pkg $root "1. Requisitos"
  $pkgRF = New-Pkg $pkgReq "1.1 Requerimientos funcionales"
  $pkgRNF = New-Pkg $pkgReq "1.2 Requerimientos no funcionales"
  $RF = @{}
  $dRF = New-Dgm $pkgRF "RF - Requerimientos funcionales" "Custom" "Requerimientos funcionales del prototipo, agrupados por módulo."
  $dRNF = New-Dgm $pkgRNF "RNF - Requerimientos no funcionales" "Custom" "Requerimientos no funcionales del prototipo."

  $e = New-El $pkgRF 'RF01 Autenticar usuarios' 'Requirement' 'Functional' 'Autenticar a cada usuario con su Código Único de Identificación Laboral (CUIL) y una contraseña almacenada con Argon2id, exigiendo el cambio de la contraseña inicial en el primer ingreso y bloqueando la cuenta tras reiterados intentos fallidos.'; $e.Alias = 'RF01'; $e.Update() | Out-Null; $RF['RF01'] = $e; Add-Obj $dRF $e 30 60 240 75 | Out-Null
  $e = New-El $pkgRF 'RF02 Administrar roles con vigencia' 'Requirement' 'Functional' 'Asignar y revocar los roles de agente, JS, OP y DH con fecha de inicio y de fin de vigencia y con la referencia del memorando que los dispone. Un usuario puede tener más de un rol al mismo tiempo.'; $e.Alias = 'RF02'; $e.Update() | Out-Null; $RF['RF02'] = $e; Add-Obj $dRF $e 30 155 240 75 | Out-Null
  $e = New-El $pkgRF 'RF03 Restringir el acceso por rol y servicio' 'Requirement' 'Functional' 'Restringir la información laboral visible y las operaciones habilitadas según el rol y el servicio del usuario: el agente accede solo a sus propios datos, la JS a los agentes de su servicio, y la OP y la DH a la totalidad del hospital.'; $e.Alias = 'RF03'; $e.Update() | Out-Null; $RF['RF03'] = $e; Add-Obj $dRF $e 30 250 240 75 | Out-Null
  $e = New-El $pkgRF 'RF04 Gestionar el padrón único de agentes' 'Requirement' 'Functional' 'Registrar las altas, las bajas lógicas y las modificaciones de los agentes del hospital (datos personales, legajo, cargo, servicio, modalidad horaria vigente y vinculación) en un padrón único que alimenta la diagramación, las novedades y el parte diario.'; $e.Alias = 'RF04'; $e.Update() | Out-Null; $RF['RF04'] = $e; Add-Obj $dRF $e 30 345 240 75 | Out-Null
  $e = New-El $pkgRF 'RF05 Importar el listado inicial de agentes' 'Requirement' 'Functional' 'Importar el listado de agentes desde un archivo de planilla de cálculo (CSV o XLSX) para la carga inicial del padrón, validando los datos obligatorios y los duplicados e informando las filas rechazadas.'; $e.Alias = 'RF05'; $e.Update() | Out-Null; $RF['RF05'] = $e; Add-Obj $dRF $e 30 440 240 75 | Out-Null
  $e = New-El $pkgRF 'RF06 Gestionar servicios y jefaturas' 'Requirement' 'Functional' 'Registrar los servicios del hospital con su agrupación, su dotación mínima por franja horaria y la JS designada con su vigencia.'; $e.Alias = 'RF06'; $e.Update() | Out-Null; $RF['RF06'] = $e; Add-Obj $dRF $e 30 535 240 75 | Out-Null
  $e = New-El $pkgRF 'RF07 Parametrizar modalidades horarias y reglas' 'Requirement' 'Functional' 'Mantener el catálogo de modalidades horarias y los parámetros de las reglas de validación asociadas (topes diario, semanal y mensual, descanso mínimo entre jornadas, guardias mínimas y máximas y franquicias), con vigencia y sin modificar el código.'; $e.Alias = 'RF07'; $e.Update() | Out-Null; $RF['RF07'] = $e; Add-Obj $dRF $e 30 630 240 75 | Out-Null
  $e = New-El $pkgRF 'RF08 Gestionar feriados' 'Requirement' 'Functional' 'Registrar el calendario anual de feriados y días no laborables que utiliza la diagramación.'; $e.Alias = 'RF08'; $e.Update() | Out-Null; $RF['RF08'] = $e; Add-Obj $dRF $e 30 725 240 75 | Out-Null
  $n = New-El $pkgRF 'Módulo 1' 'Text' '' 'Módulo 1: Padrón, roles y seguridad'; Add-Obj $dRF $n 30 15 240 35 | Out-Null
  $e = New-El $pkgRF 'RF09 Confeccionar el diagrama mensual' 'Requirement' 'Functional' 'Confeccionar, por parte de cada JS, el diagrama mensual de su servicio en una grilla agente por día, generada inicialmente a partir de la modalidad horaria de cada agente y editable por jornada (turno, guardia activa o pasiva, franco, franco compensatorio o licencia), incluidas las jornadas que cruzan la medianoche.'; $e.Alias = 'RF09'; $e.Update() | Out-Null; $RF['RF09'] = $e; Add-Obj $dRF $e 290 60 240 75 | Out-Null
  $e = New-El $pkgRF 'RF10 Validar el diagrama al cargarlo' 'Requirement' 'Functional' 'Validar automáticamente el diagrama contra las reglas de la modalidad de cada agente y las reglas transversales (superposición de jornadas, descanso mínimo de diez horas, topes horarios, guardias mínimas del personal feriante y dotación mínima del servicio), distinguiendo errores bloqueantes de advertencias e indicando agente, fecha y regla infringida en el momento de la carga.'; $e.Alias = 'RF10'; $e.Update() | Out-Null; $RF['RF10'] = $e; Add-Obj $dRF $e 290 155 240 75 | Out-Null
  $e = New-El $pkgRF 'RF11 Presentar el diagrama a la OP' 'Requirement' 'Functional' 'Registrar la presentación del diagrama por la JS con sus estados (borrador, pendiente de aprobación, aprobado y observado), impidiendo la presentación con errores bloqueantes y conservando la versión para el control de concurrencia.'; $e.Alias = 'RF11'; $e.Update() | Out-Null; $RF['RF11'] = $e; Add-Obj $dRF $e 290 250 240 75 | Out-Null
  $e = New-El $pkgRF 'RF12 Controlar y aprobar diagramas' 'Requirement' 'Functional' 'Ofrecer a la OP una bandeja de diagramas por estado y servicio desde la cual aprobarlos u observarlos con comentarios. El diagrama aprobado no se modifica salvo mediante novedades aprobadas.'; $e.Alias = 'RF12'; $e.Update() | Out-Null; $RF['RF12'] = $e; Add-Obj $dRF $e 290 345 240 75 | Out-Null
  $e = New-El $pkgRF 'RF13 Consultar el diagrama vigente' 'Requirement' 'Functional' 'Mostrar a cada agente su propia diagramación (o la del servicio completo, cuando la JS lo habilita), a la JS la de su servicio, y a la OP y la DH la de todos los servicios.'; $e.Alias = 'RF13'; $e.Update() | Out-Null; $RF['RF13'] = $e; Add-Obj $dRF $e 290 440 240 75 | Out-Null
  $e = New-El $pkgRF 'RF14 Alertar la presentación del diagrama' 'Requirement' 'Functional' 'Recordar a las JS la presentación del diagrama del mes siguiente y mostrar a la OP los servicios que no lo presentaron.'; $e.Alias = 'RF14'; $e.Update() | Out-Null; $RF['RF14'] = $e; Add-Obj $dRF $e 290 535 240 75 | Out-Null
  $n = New-El $pkgRF 'Módulo 2' 'Text' '' 'Módulo 2: Diagramación de servicios'; Add-Obj $dRF $n 290 15 240 35 | Out-Null
  $e = New-El $pkgRF 'RF15 Registrar solicitudes de novedad' 'Requirement' 'Functional' 'Registrar solicitudes de novedad por tipo (cambio de guardia, cambio de horario, FC, horas franco, PSCR, PSSR, devolución de pase de salida, falta justificada o injustificada, horas extras u omisión de marcación) con agente, fechas y horas, agente contraparte cuando corresponde, motivo y documentación de respaldo.'; $e.Alias = 'RF15'; $e.Update() | Out-Null; $RF['RF15'] = $e; Add-Obj $dRF $e 550 60 240 75 | Out-Null
  $e = New-El $pkgRF 'RF16 Validar la novedad al cargarla' 'Requirement' 'Functional' 'Validar cada novedad en el momento de la carga: compatibilidad del cambio de guardia entre los agentes involucrados, descanso mínimo, saldo de horas disponible para francos compensatorios, horas franco posteriores al egreso diagramado y sin superar las 12 hs desde el ingreso diagramado (salvo guardias), saldo de horas disponibles dentro de los plazos vigentes para la devolución de pases de salida, topes mensual y diario de pases de salida particulares, superposición con licencias y tope mensual de faltas justificadas que exige la intervención de la Secretaría de Salud.'; $e.Alias = 'RF16'; $e.Update() | Out-Null; $RF['RF16'] = $e; Add-Obj $dRF $e 550 155 240 75 | Out-Null
  $e = New-El $pkgRF 'RF17 Tramitar la autorización por instancias' 'Requirement' 'Functional' 'Tramitar cada novedad por instancias sucesivas (la JS, o la DH cuando el solicitante es una JS, luego la verificación formal de la OP y por último la aprobación de la DH), con autorización, observación con devolución para corrección o rechazo en cada una.'; $e.Alias = 'RF17'; $e.Update() | Out-Null; $RF['RF17'] = $e; Add-Obj $dRF $e 550 250 240 75 | Out-Null
  $e = New-El $pkgRF 'RF18 Conservar la trazabilidad de cada decisión' 'Requirement' 'Functional' 'Registrar para cada instancia quién decidió, cuándo, con qué resultado y con qué fundamento, y ofrecer el historial completo de cada novedad a los actores con competencia sobre ella.'; $e.Alias = 'RF18'; $e.Update() | Out-Null; $RF['RF18'] = $e; Add-Obj $dRF $e 550 345 240 75 | Out-Null
  $e = New-El $pkgRF 'RF19 Notificar el resultado de cada instancia' 'Requirement' 'Functional' 'Notificar al agente y a la JS, en la aplicación y por correo institucional, cada cambio de estado de la novedad.'; $e.Alias = 'RF19'; $e.Update() | Out-Null; $RF['RF19'] = $e; Add-Obj $dRF $e 550 440 240 75 | Out-Null
  $e = New-El $pkgRF 'RF20 Aplicar la novedad aprobada' 'Requirement' 'Functional' 'Aplicar automáticamente la novedad aprobada sobre el diagrama vigente (jornada modificada o intercambiada, franco compensatorio descontado en modo FIFO del banco de horas) y registrar las licencias tramitadas en los sistemas municipales como novedades de origen externo, sin gestionar su tramitación.'; $e.Alias = 'RF20'; $e.Update() | Out-Null; $RF['RF20'] = $e; Add-Obj $dRF $e 550 535 240 75 | Out-Null
  $n = New-El $pkgRF 'Módulo 3' 'Text' '' 'Módulo 3: Novedades y trazabilidad'; Add-Obj $dRF $n 550 15 240 35 | Out-Null
  $e = New-El $pkgRF 'RF21 Importar marcaciones' 'Requirement' 'Functional' 'Importar las marcaciones desde el archivo exportado por la aplicación del sistema de relojes biométricos, asociarlas a cada agente y conformar las jornadas registradas (ingreso y egreso), detectando las marcaciones faltantes.'; $e.Alias = 'RF21'; $e.Update() | Out-Null; $RF['RF21'] = $e; Add-Obj $dRF $e 810 60 240 75 | Out-Null
  $e = New-El $pkgRF 'RF22 Cotejar la asistencia' 'Requirement' 'Functional' 'Cotejar automáticamente, para cada agente y día, el diagrama vigente con las jornadas registradas y las novedades aprobadas, y detectar inasistencias, llegadas tarde, retiros anticipados, omisiones de marcación y guardias no cubiertas.'; $e.Alias = 'RF22'; $e.Update() | Out-Null; $RF['RF22'] = $e; Add-Obj $dRF $e 810 155 240 75 | Out-Null
  $e = New-El $pkgRF 'RF23 Generar el parte diario' 'Requirement' 'Functional' 'Generar automáticamente el parte diario a partir del cotejo, con el código correspondiente a cada agente y día (el PSSR se informa con el código 333 y la hora de salida registrada) y por separado según la vinculación del agente (personal en relación de dependencia, por un lado, y monotributistas y becarios, por el otro), y permitir a la OP revisarlo, completarlo con cargas manuales fundamentadas y cerrarlo con registro del responsable y la fecha.'; $e.Alias = 'RF23'; $e.Update() | Out-Null; $RF['RF23'] = $e; Add-Obj $dRF $e 810 250 240 75 | Out-Null
  $e = New-El $pkgRF 'RF24 Calcular y consultar los indicadores' 'Requirement' 'Functional' 'Calcular y mostrar los indicadores de ausentismo por tipo de novedad y de cobertura, ambos por servicio y por período, a partir de los diagramas aprobados y de los partes diarios cerrados, y restringir su consulta a la OP y a la DH.'; $e.Alias = 'RF24'; $e.Update() | Out-Null; $RF['RF24'] = $e; Add-Obj $dRF $e 810 345 240 75 | Out-Null
  $n = New-El $pkgRF 'Módulo 4' 'Text' '' 'Módulo 4: Control de asistencia y parte diario'; Add-Obj $dRF $n 810 15 240 35 | Out-Null
  $e = New-El $pkgRNF 'RNF01 Seguridad' 'Requirement' 'Non-Functional' '- Contraseñas almacenadas únicamente como hash Argon2id.
- Control de acceso basado en roles (RBAC) con separación de funciones: quien solicita una novedad no puede autorizarla.
- Bloqueo de la cuenta tras cinco intentos fallidos consecutivos y cierre de sesión por inactividad.'; $e.Alias = 'RNF01'; Add-Obj $dRNF $e 30 30 240 80 | Out-Null
  $e = New-El $pkgRNF 'RNF02 Privacidad de los datos laborales' 'Requirement' 'Non-Functional' '- Medidas técnicas y organizativas que garanticen la seguridad y confidencialidad de los datos personales (Ley 25.326, 2000, art. 9): cada rol accede solo a la información necesaria para su función.
- Los datos personales y laborales permanecen en la infraestructura del hospital y no se transfieren a servicios externos.'; $e.Alias = 'RNF02'; Add-Obj $dRNF $e 290 30 240 80 | Out-Null
  $e = New-El $pkgRNF 'RNF03 Trazabilidad e integridad' 'Requirement' 'Non-Functional' '- Registro de auditoría implementado en la base de datos mediante disparadores, de solo escritura para la aplicación y para los usuarios.
- Integridad referencial y transaccional entre cada operación y su asiento de auditoría.
- Control de concurrencia optimista por versión en diagramas, novedades y partes diarios.'; $e.Alias = 'RNF03'; Add-Obj $dRNF $e 550 30 240 80 | Out-Null
  $e = New-El $pkgRNF 'RNF04 Disponibilidad y continuidad' 'Requirement' 'Non-Functional' '- Operación dentro de la red interna del hospital, con la base de datos en un servidor dedicado del centro de datos virtualizado.
- Respaldo diario automatizado de la base de datos con retención mínima de treinta días.
- Funcionamiento sin dependencia del sistema de relojes: la importación de marcaciones es diferida y repetible.'; $e.Alias = 'RNF04'; Add-Obj $dRNF $e 810 30 240 80 | Out-Null
  $e = New-El $pkgRNF 'RNF05 Rendimiento' 'Requirement' 'Non-Functional' '- Validación completa del diagrama mensual de un servicio (hasta sesenta agentes por treinta y un días) en menos de dos segundos.
- Cotejo diario de la totalidad del personal (seiscientos agentes) en menos de treinta segundos.
- Respuesta de las consultas interactivas en menos de un segundo.'; $e.Alias = 'RNF05'; Add-Obj $dRNF $e 1070 30 240 80 | Out-Null
  $e = New-El $pkgRNF 'RNF06 Usabilidad' 'Requirement' 'Non-Functional' '- Interfaz de escritorio con grilla agente por día, edición por doble clic y retroalimentación inmediata de la validación mediante colores y mensajes.
- Textos, mensajes de error y ayuda contextual en español, orientados a personal administrativo no técnico.
- Los flujos principales (confeccionar el diagrama, solicitar una novedad y generar el parte diario) se completan sin abandonar la pantalla de trabajo.'; $e.Alias = 'RNF06'; Add-Obj $dRNF $e 30 130 240 80 | Out-Null
  $e = New-El $pkgRNF 'RNF07 Portabilidad' 'Requirement' 'Non-Functional' '- Aplicación Java 25 con JavaFX, ejecutable en las estaciones de trabajo Windows y GNU/Linux del hospital sin recompilación.
- Base de datos PostgreSQL 18, desplegable en un servidor GNU/Linux o Windows.'; $e.Alias = 'RNF07'; Add-Obj $dRNF $e 290 130 240 80 | Out-Null
  $e = New-El $pkgRNF 'RNF08 Mantenibilidad' 'Requirement' 'Non-Functional' '- Arquitectura en capas (presentación, servicios, repositorios y dominio) que permite incorporar una interfaz web sin rediseñar el núcleo.
- Reglas de validación parametrizadas en la base de datos, modificables sin recompilar.
- Pruebas unitarias automatizadas del motor de validación con JUnit 5 y control de versiones con Git.'; $e.Alias = 'RNF08'; Add-Obj $dRNF $e 550 130 240 80 | Out-Null
  $e = New-El $pkgRNF 'RNF09 Interoperabilidad' 'Requirement' 'Non-Functional' '- Importación de marcaciones en los formatos que exporta la aplicación del sistema de relojes (CSV y XLSX).
- Importación del listado inicial de agentes en formato CSV o XLSX.
- Notificaciones por correo a través del servidor de correo institucional.'; $e.Alias = 'RNF09'; Add-Obj $dRNF $e 810 130 240 80 | Out-Null
  $e = New-El $pkgRNF 'RNF10 Escalabilidad' 'Requirement' 'Non-Functional' '- Soporte de al menos mil doscientos agentes activos (los tres hospitales municipales) y diez años de historial sin cambios de diseño.'; $e.Alias = 'RNF10'; Add-Obj $dRNF $e 1070 130 240 80 | Out-Null
  Export-Png $dRF 'RF-requerimientos-funcionales'
  Export-Png $dRNF 'RNF-requerimientos-no-funcionales'

  # ===== 2. Casos de uso ======================================================
  Log "Paquete Casos de uso"
  $pkgCU = New-Pkg $root "2. Casos de uso"
  $pkgAct = New-Pkg $pkgCU "2.0 Actores"
  $ACT = @{}
  $ACT['AGENTE'] = New-El $pkgAct 'Agente' 'Actor' '' 'Agente del HMU: consulta su diagramación y solicita novedades.'
  $ACT['JS'] = New-El $pkgAct 'Jefatura de Servicio' 'Actor' '' 'Confecciona y presenta el diagrama de su servicio y autoriza en primera instancia las novedades de sus agentes.'
  $ACT['OP'] = New-El $pkgAct 'Oficina de Personal' 'Actor' '' 'Mantiene el padrón, controla los diagramas, verifica las novedades, coteja la asistencia y confecciona el parte diario.'
  $ACT['DH'] = New-El $pkgAct 'Dirección Hospitalaria' 'Actor' '' 'Designa las jefaturas, aprueba en última instancia las novedades y consulta los indicadores.'
  $ACT['RELOJ'] = New-El $pkgAct 'Sistema de control de asistencia' 'Actor' '' 'Aplicación web del proveedor de relojes biométricos; provee el archivo de marcaciones.'
  $dAct = New-Dgm $pkgAct 'Actores del sistema' 'Use Case' 'Jerarquía de actores: las jefaturas, la Oficina de Personal y la Dirección son agentes con responsabilidades adicionales.'
  Add-Obj $dAct $ACT['AGENTE'] 320 30 90 90 | Out-Null
  Add-Obj $dAct $ACT['JS'] 120 200 90 90 | Out-Null; Add-Obj $dAct $ACT['OP'] 320 200 90 90 | Out-Null; Add-Obj $dAct $ACT['DH'] 520 200 90 90 | Out-Null
  Add-Obj $dAct $ACT['RELOJ'] 720 30 110 90 | Out-Null
  $GENIDS = @()
  $c = New-Con $ACT['JS'] $ACT['AGENTE'] 'Generalization' '' ''; $GENIDS += $c.ConnectorID
  $c = New-Con $ACT['OP'] $ACT['AGENTE'] 'Generalization' '' ''; $GENIDS += $c.ConnectorID
  $c = New-Con $ACT['DH'] $ACT['AGENTE'] 'Generalization' '' ''; $GENIDS += $c.ConnectorID
  Export-Png $dAct 'CU00-actores'
  $pkgCUG = New-Pkg $pkgCU '2.1 Caso de uso general'
  $CUG = @{}
  $dCUG = New-Dgm $pkgCUG 'CU-General: Sistema de diagramación de servicios y gestión del ausentismo (HMU)' 'Use Case' 'Diagrama de caso de uso general: funciones principales del prototipo y actores involucrados. Cada caso de uso general se detalla en el paquete del módulo correspondiente.'
  $bnd = New-El $pkgCUG 'Sistema de diagramación de servicios y gestión del ausentismo (HMU)' 'Boundary' '' ''
  $bo = Add-Obj $dCUG $bnd 250 130 810 510; $bo.Sequence = 500; $bo.Update() | Out-Null
  $e = New-El $pkgCUG 'Diagramar el servicio' 'UseCase' '' ''; $e.Alias = 'CUG03'; $e.Update() | Out-Null; $CUG['CUG03'] = $e; Add-Obj $dCUG $e 280 170 190 60 | Out-Null
  New-Con $e $RF['RF09'] 'Realisation' '' '' | Out-Null
  New-Con $e $RF['RF10'] 'Realisation' '' '' | Out-Null
  New-Con $e $RF['RF11'] 'Realisation' '' '' | Out-Null
  New-Con $e $RF['RF14'] 'Realisation' '' '' | Out-Null
  $e = New-El $pkgCUG 'Autenticarse en el sistema' 'UseCase' '' ''; $e.Alias = 'CUG01'; $e.Update() | Out-Null; $CUG['CUG01'] = $e; Add-Obj $dCUG $e 280 360 190 60 | Out-Null
  New-Con $e $RF['RF01'] 'Realisation' '' '' | Out-Null
  New-Con $e $RF['RF03'] 'Realisation' '' '' | Out-Null
  $e = New-El $pkgCUG 'Consultar la diagramación vigente' 'UseCase' '' ''; $e.Alias = 'CUG05'; $e.Update() | Out-Null; $CUG['CUG05'] = $e; Add-Obj $dCUG $e 280 455 190 60 | Out-Null
  New-Con $e $RF['RF13'] 'Realisation' '' '' | Out-Null
  $e = New-El $pkgCUG 'Gestionar novedades con trazabilidad' 'UseCase' '' ''; $e.Alias = 'CUG06'; $e.Update() | Out-Null; $CUG['CUG06'] = $e; Add-Obj $dCUG $e 560 265 190 60 | Out-Null
  New-Con $e $RF['RF15'] 'Realisation' '' '' | Out-Null
  New-Con $e $RF['RF16'] 'Realisation' '' '' | Out-Null
  New-Con $e $RF['RF17'] 'Realisation' '' '' | Out-Null
  New-Con $e $RF['RF18'] 'Realisation' '' '' | Out-Null
  New-Con $e $RF['RF19'] 'Realisation' '' '' | Out-Null
  New-Con $e $RF['RF20'] 'Realisation' '' '' | Out-Null
  $e = New-El $pkgCUG 'Controlar la asistencia y generar el parte diario' 'UseCase' '' ''; $e.Alias = 'CUG07'; $e.Update() | Out-Null; $CUG['CUG07'] = $e; Add-Obj $dCUG $e 840 170 190 60 | Out-Null
  New-Con $e $RF['RF21'] 'Realisation' '' '' | Out-Null
  New-Con $e $RF['RF22'] 'Realisation' '' '' | Out-Null
  New-Con $e $RF['RF23'] 'Realisation' '' '' | Out-Null
  $e = New-El $pkgCUG 'Controlar el diagrama' 'UseCase' '' ''; $e.Alias = 'CUG04'; $e.Update() | Out-Null; $CUG['CUG04'] = $e; Add-Obj $dCUG $e 840 360 190 60 | Out-Null
  New-Con $e $RF['RF12'] 'Realisation' '' '' | Out-Null
  $e = New-El $pkgCUG 'Gestionar padrón, servicios y reglas' 'UseCase' '' ''; $e.Alias = 'CUG02'; $e.Update() | Out-Null; $CUG['CUG02'] = $e; Add-Obj $dCUG $e 840 455 190 60 | Out-Null
  New-Con $e $RF['RF02'] 'Realisation' '' '' | Out-Null
  New-Con $e $RF['RF04'] 'Realisation' '' '' | Out-Null
  New-Con $e $RF['RF05'] 'Realisation' '' '' | Out-Null
  New-Con $e $RF['RF06'] 'Realisation' '' '' | Out-Null
  New-Con $e $RF['RF07'] 'Realisation' '' '' | Out-Null
  New-Con $e $RF['RF08'] 'Realisation' '' '' | Out-Null
  $e = New-El $pkgCUG 'Consultar indicadores de ausentismo y cobertura' 'UseCase' '' ''; $e.Alias = 'CUG08'; $e.Update() | Out-Null; $CUG['CUG08'] = $e; Add-Obj $dCUG $e 840 550 190 60 | Out-Null
  New-Con $e $RF['RF24'] 'Realisation' '' '' | Out-Null
  Add-Obj $dCUG $ACT['JS'] 60 185 100 90 | Out-Null
  Add-Obj $dCUG $ACT['AGENTE'] 60 305 100 90 | Out-Null
  Add-Obj $dCUG $ACT['DH'] 605 680 100 90 | Out-Null
  Add-Obj $dCUG $ACT['RELOJ'] 1110 100 100 90 | Out-Null
  Add-Obj $dCUG $ACT['OP'] 1110 250 100 90 | Out-Null
  New-Con $ACT['AGENTE'] $CUG['CUG01'] 'Association' '' '' | Out-Null
  New-Con $ACT['AGENTE'] $CUG['CUG05'] 'Association' '' '' | Out-Null
  New-Con $ACT['AGENTE'] $CUG['CUG06'] 'Association' '' '' | Out-Null
  New-Con $ACT['JS'] $CUG['CUG03'] 'Association' '' '' | Out-Null
  New-Con $ACT['JS'] $CUG['CUG06'] 'Association' '' '' | Out-Null
  New-Con $ACT['OP'] $CUG['CUG06'] 'Association' '' '' | Out-Null
  New-Con $ACT['DH'] $CUG['CUG06'] 'Association' '' '' | Out-Null
  New-Con $ACT['OP'] $CUG['CUG07'] 'Association' '' '' | Out-Null
  New-Con $ACT['RELOJ'] $CUG['CUG07'] 'Association' '' '' | Out-Null
  New-Con $ACT['OP'] $CUG['CUG04'] 'Association' '' '' | Out-Null
  New-Con $ACT['OP'] $CUG['CUG02'] 'Association' '' '' | Out-Null
  New-Con $ACT['OP'] $CUG['CUG08'] 'Association' '' '' | Out-Null
  New-Con $ACT['DH'] $CUG['CUG08'] 'Association' '' '' | Out-Null
  Hide-Links $dCUG $GENIDS
  Export-Png $dCUG 'CU-general'
  $CU = @{}
  $pkgM1 = New-Pkg $pkgCU '2.2 M1 Padrón, roles y seguridad'
  $dM1 = New-Dgm $pkgM1 'CU-M1 Padrón, roles y seguridad' 'Use Case' 'Casos de uso del módulo 1: Padrón, roles y seguridad.'
  $bnd = New-El $pkgM1 'Sistema de diagramación de servicios y gestión del ausentismo (HMU)' 'Boundary' '' ''
  $bo = Add-Obj $dM1 $bnd 250 20 560 710; $bo.Sequence = 500; $bo.Update() | Out-Null
  $e = New-El $pkgM1 'Autenticarse' 'UseCase' '' 'El usuario ingresa su CUIL y contraseña; el sistema verifica el hash Argon2id, abre la sesión y carga los roles vigentes.'; $e.Alias = 'CU01'; $e.Update() | Out-Null; $CU['CU01'] = $e; Add-Obj $dM1 $e 280 70 190 60 | Out-Null
  New-Con $e $RF['RF01'] 'Realisation' '' '' | Out-Null
  New-Con $e $RF['RF03'] 'Realisation' '' '' | Out-Null
  $e = New-El $pkgM1 'Cambiar contraseña' 'UseCase' '' 'El usuario reemplaza su contraseña; obligatorio en el primer ingreso o tras un restablecimiento.'; $e.Alias = 'CU02'; $e.Update() | Out-Null; $CU['CU02'] = $e; Add-Obj $dM1 $e 280 165 190 60 | Out-Null
  New-Con $e $RF['RF01'] 'Realisation' '' '' | Out-Null
  $e = New-El $pkgM1 'Gestionar agentes' 'UseCase' '' 'Alta, baja lógica y modificación de un agente del padrón único: datos personales, legajo, cargo, servicio, modalidad horaria vigente y vinculación.'; $e.Alias = 'CU03'; $e.Update() | Out-Null; $CU['CU03'] = $e; Add-Obj $dM1 $e 280 260 190 60 | Out-Null
  New-Con $e $RF['RF04'] 'Realisation' '' '' | Out-Null
  $e = New-El $pkgM1 'Asignar modalidad horaria' 'UseCase' '' 'Se asocia al agente la modalidad horaria (y la franquicia, si corresponde) que rige su diagramación.'; $e.Alias = 'CU04'; $e.Update() | Out-Null; $CU['CU04'] = $e; Add-Obj $dM1 $e 530 355 190 60 | Out-Null
  New-Con $e $RF['RF04'] 'Realisation' '' '' | Out-Null
  New-Con $e $RF['RF07'] 'Realisation' '' '' | Out-Null
  $e = New-El $pkgM1 'Importar listado de agentes' 'UseCase' '' 'Carga inicial del padrón desde un archivo CSV o XLSX; el sistema valida los datos obligatorios y los duplicados e informa las filas rechazadas.'; $e.Alias = 'CU05'; $e.Update() | Out-Null; $CU['CU05'] = $e; Add-Obj $dM1 $e 280 355 190 60 | Out-Null
  New-Con $e $RF['RF05'] 'Realisation' '' '' | Out-Null
  $e = New-El $pkgM1 'Asignar roles con vigencia' 'UseCase' '' 'Se otorga o revoca un rol con fecha desde y hasta y la referencia del memorando que lo dispone.'; $e.Alias = 'CU06'; $e.Update() | Out-Null; $CU['CU06'] = $e; Add-Obj $dM1 $e 530 260 190 60 | Out-Null
  New-Con $e $RF['RF02'] 'Realisation' '' '' | Out-Null
  New-Con $e $RF['RF03'] 'Realisation' '' '' | Out-Null
  $e = New-El $pkgM1 'Gestionar servicios' 'UseCase' '' 'Alta y modificación de servicios: agrupación, dotación mínima por franja y jefatura vigente.'; $e.Alias = 'CU07'; $e.Update() | Out-Null; $CU['CU07'] = $e; Add-Obj $dM1 $e 280 450 190 60 | Out-Null
  New-Con $e $RF['RF06'] 'Realisation' '' '' | Out-Null
  $e = New-El $pkgM1 'Parametrizar reglas de validación' 'UseCase' '' 'Se mantienen los parámetros de las reglas de cada modalidad (topes, descansos, guardias mínimas) con vigencia.'; $e.Alias = 'CU08'; $e.Update() | Out-Null; $CU['CU08'] = $e; Add-Obj $dM1 $e 280 545 190 60 | Out-Null
  New-Con $e $RF['RF07'] 'Realisation' '' '' | Out-Null
  $e = New-El $pkgM1 'Gestionar feriados' 'UseCase' '' 'Carga anual del calendario de feriados y días no laborables.'; $e.Alias = 'CU09'; $e.Update() | Out-Null; $CU['CU09'] = $e; Add-Obj $dM1 $e 280 640 190 60 | Out-Null
  New-Con $e $RF['RF08'] 'Realisation' '' '' | Out-Null
  Add-Obj $dM1 $ACT['AGENTE'] 60 80 90 90 | Out-Null
  Add-Obj $dM1 $ACT['OP'] 60 420 90 90 | Out-Null
  New-Con $ACT['AGENTE'] $CU['CU01'] 'Association' '' '' | Out-Null
  New-Con $ACT['AGENTE'] $CU['CU02'] 'Association' '' '' | Out-Null
  New-Con $ACT['OP'] $CU['CU03'] 'Association' '' '' | Out-Null
  New-Con $ACT['OP'] $CU['CU05'] 'Association' '' '' | Out-Null
  New-Con $ACT['OP'] $CU['CU07'] 'Association' '' '' | Out-Null
  New-Con $ACT['OP'] $CU['CU08'] 'Association' '' '' | Out-Null
  New-Con $ACT['OP'] $CU['CU09'] 'Association' '' '' | Out-Null
  $c = New-Con $CU['CU02'] $CU['CU01'] 'UseCase' 'extend' ''; $c.Subtype = 'Extends'; $c.Update() | Out-Null; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null
  $c = New-Con $CU['CU03'] $CU['CU04'] 'UseCase' 'include' ''; $c.Subtype = 'Includes'; $c.Update() | Out-Null; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null
  $c = New-Con $CU['CU06'] $CU['CU03'] 'UseCase' 'extend' ''; $c.Subtype = 'Extends'; $c.Update() | Out-Null; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null
  Hide-Links $dM1 $GENIDS
  Export-Png $dM1 'CU-M1-Padrón_roles_y_seguridad'
  $pkgM2 = New-Pkg $pkgCU '2.3 M2 Diagramación de servicios'
  $dM2 = New-Dgm $pkgM2 'CU-M2 Diagramación de servicios' 'Use Case' 'Casos de uso del módulo 2: Diagramación de servicios.'
  $bnd = New-El $pkgM2 'Sistema de diagramación de servicios y gestión del ausentismo (HMU)' 'Boundary' '' ''
  $bo = Add-Obj $dM2 $bnd 250 20 560 615; $bo.Sequence = 500; $bo.Update() | Out-Null
  $e = New-El $pkgM2 'Confeccionar diagrama mensual' 'UseCase' '' 'La jefatura crea el borrador del período, obtiene la grilla inicial generada desde las modalidades y edita las jornadas de cada agente.'; $e.Alias = 'CU10'; $e.Update() | Out-Null; $CU['CU10'] = $e; Add-Obj $dM2 $e 280 165 190 60 | Out-Null
  New-Con $e $RF['RF09'] 'Realisation' '' '' | Out-Null
  $e = New-El $pkgM2 'Generar grilla inicial' 'UseCase' '' 'El sistema expande la modalidad horaria de cada agente sobre el calendario del período, considerando feriados y franquicias.'; $e.Alias = 'CU11'; $e.Update() | Out-Null; $CU['CU11'] = $e; Add-Obj $dM2 $e 530 70 190 60 | Out-Null
  New-Con $e $RF['RF09'] 'Realisation' '' '' | Out-Null
  $e = New-El $pkgM2 'Editar jornada' 'UseCase' '' 'Modificación del tipo y horario de una jornada (turno, guardia, franco), con cruce de medianoche.'; $e.Alias = 'CU12'; $e.Update() | Out-Null; $CU['CU12'] = $e; Add-Obj $dM2 $e 530 165 190 60 | Out-Null
  New-Con $e $RF['RF09'] 'Realisation' '' '' | Out-Null
  $e = New-El $pkgM2 'Validar diagrama' 'UseCase' '' 'El motor evalúa las reglas de la modalidad de cada agente y las reglas transversales, devolviendo errores bloqueantes y advertencias.'; $e.Alias = 'CU13'; $e.Update() | Out-Null; $CU['CU13'] = $e; Add-Obj $dM2 $e 530 260 190 60 | Out-Null
  New-Con $e $RF['RF10'] 'Realisation' '' '' | Out-Null
  $e = New-El $pkgM2 'Presentar diagrama' 'UseCase' '' 'Envío del diagrama a la Oficina de Personal; solo se admite sin errores bloqueantes.'; $e.Alias = 'CU14'; $e.Update() | Out-Null; $CU['CU14'] = $e; Add-Obj $dM2 $e 280 260 190 60 | Out-Null
  New-Con $e $RF['RF11'] 'Realisation' '' '' | Out-Null
  $e = New-El $pkgM2 'Controlar diagrama' 'UseCase' '' 'La Oficina de Personal revisa el diagrama presentado desde la bandeja por estado y servicio.'; $e.Alias = 'CU15'; $e.Update() | Out-Null; $CU['CU15'] = $e; Add-Obj $dM2 $e 280 450 190 60 | Out-Null
  New-Con $e $RF['RF12'] 'Realisation' '' '' | Out-Null
  $e = New-El $pkgM2 'Aprobar diagrama' 'UseCase' '' 'El diagrama pasa a Aprobado y no se modifica salvo mediante novedades aprobadas; se notifica a la jefatura.'; $e.Alias = 'CU16'; $e.Update() | Out-Null; $CU['CU16'] = $e; Add-Obj $dM2 $e 530 450 190 60 | Out-Null
  New-Con $e $RF['RF12'] 'Realisation' '' '' | Out-Null
  $e = New-El $pkgM2 'Observar diagrama' 'UseCase' '' 'El diagrama vuelve a la jefatura con comentarios para su corrección.'; $e.Alias = 'CU17'; $e.Update() | Out-Null; $CU['CU17'] = $e; Add-Obj $dM2 $e 530 545 190 60 | Out-Null
  New-Con $e $RF['RF12'] 'Realisation' '' '' | Out-Null
  $e = New-El $pkgM2 'Consultar diagrama vigente' 'UseCase' '' 'Cada agente ve su diagramación (o la del servicio completo); la jefatura, la Oficina de Personal y la Dirección según su alcance.'; $e.Alias = 'CU18'; $e.Update() | Out-Null; $CU['CU18'] = $e; Add-Obj $dM2 $e 280 545 190 60 | Out-Null
  New-Con $e $RF['RF13'] 'Realisation' '' '' | Out-Null
  $e = New-El $pkgM2 'Alertar presentación pendiente' 'UseCase' '' 'Al iniciar sesión, la jefatura recibe el recordatorio del diagrama del mes siguiente y la Oficina de Personal el resumen de servicios que no lo presentaron.'; $e.Alias = 'CU19'; $e.Update() | Out-Null; $CU['CU19'] = $e; Add-Obj $dM2 $e 280 355 190 60 | Out-Null
  New-Con $e $RF['RF14'] 'Realisation' '' '' | Out-Null
  Add-Obj $dM2 $ACT['JS'] 60 190 90 90 | Out-Null
  Add-Obj $dM2 $ACT['OP'] 60 400 90 90 | Out-Null
  Add-Obj $dM2 $ACT['AGENTE'] 60 540 90 90 | Out-Null
  New-Con $ACT['JS'] $CU['CU10'] 'Association' '' '' | Out-Null
  New-Con $ACT['JS'] $CU['CU14'] 'Association' '' '' | Out-Null
  New-Con $ACT['JS'] $CU['CU19'] 'Association' '' '' | Out-Null
  New-Con $ACT['OP'] $CU['CU19'] 'Association' '' '' | Out-Null
  New-Con $ACT['OP'] $CU['CU15'] 'Association' '' '' | Out-Null
  New-Con $ACT['AGENTE'] $CU['CU18'] 'Association' '' '' | Out-Null
  $c = New-Con $CU['CU10'] $CU['CU11'] 'UseCase' 'include' ''; $c.Subtype = 'Includes'; $c.Update() | Out-Null; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null
  $c = New-Con $CU['CU10'] $CU['CU12'] 'UseCase' 'include' ''; $c.Subtype = 'Includes'; $c.Update() | Out-Null; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null
  $c = New-Con $CU['CU10'] $CU['CU13'] 'UseCase' 'include' ''; $c.Subtype = 'Includes'; $c.Update() | Out-Null; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null
  $c = New-Con $CU['CU14'] $CU['CU13'] 'UseCase' 'include' ''; $c.Subtype = 'Includes'; $c.Update() | Out-Null; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null
  $c = New-Con $CU['CU16'] $CU['CU15'] 'UseCase' 'extend' ''; $c.Subtype = 'Extends'; $c.Update() | Out-Null; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null
  $c = New-Con $CU['CU17'] $CU['CU15'] 'UseCase' 'extend' ''; $c.Subtype = 'Extends'; $c.Update() | Out-Null; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null
  Hide-Links $dM2 $GENIDS
  Export-Png $dM2 'CU-M2-Diagramación_de_servicios'
  $pkgM3 = New-Pkg $pkgCU '2.4 M3 Novedades y trazabilidad'
  $dM3 = New-Dgm $pkgM3 'CU-M3 Novedades y trazabilidad' 'Use Case' 'Casos de uso del módulo 3: Novedades y trazabilidad.'
  $bnd = New-El $pkgM3 'Sistema de diagramación de servicios y gestión del ausentismo (HMU)' 'Boundary' '' ''
  $bo = Add-Obj $dM3 $bnd 250 20 560 615; $bo.Sequence = 500; $bo.Update() | Out-Null
  $e = New-El $pkgM3 'Solicitar novedad' 'UseCase' '' 'El agente registra la solicitud (tipo, fechas, contraparte, motivo, respaldo); si el solicitante es una jefatura, la primera instancia es la Dirección.'; $e.Alias = 'CU20'; $e.Update() | Out-Null; $CU['CU20'] = $e; Add-Obj $dM3 $e 280 70 190 60 | Out-Null
  New-Con $e $RF['RF15'] 'Realisation' '' '' | Out-Null
  $e = New-El $pkgM3 'Validar novedad' 'UseCase' '' 'El motor verifica la compatibilidad del cambio, el descanso mínimo, el saldo vigente del banco de horas, las horas franco, los topes y la devolución de los pases de salida, la superposición con licencias y el tope de faltas justificadas.'; $e.Alias = 'CU21'; $e.Update() | Out-Null; $CU['CU21'] = $e; Add-Obj $dM3 $e 530 70 190 60 | Out-Null
  New-Con $e $RF['RF16'] 'Realisation' '' '' | Out-Null
  $e = New-El $pkgM3 'Autorizar novedad' 'UseCase' '' 'Primera instancia: la jefatura autoriza, observa o rechaza según la cobertura del servicio.'; $e.Alias = 'CU22'; $e.Update() | Out-Null; $CU['CU22'] = $e; Add-Obj $dM3 $e 280 260 190 60 | Out-Null
  New-Con $e $RF['RF17'] 'Realisation' '' '' | Out-Null
  New-Con $e $RF['RF18'] 'Realisation' '' '' | Out-Null
  $e = New-El $pkgM3 'Verificar novedad' 'UseCase' '' 'Segunda instancia: la Oficina de Personal controla fechas, códigos, saldos y descanso mínimo; devolución para corrección si hay errores.'; $e.Alias = 'CU23'; $e.Update() | Out-Null; $CU['CU23'] = $e; Add-Obj $dM3 $e 280 355 190 60 | Out-Null
  New-Con $e $RF['RF17'] 'Realisation' '' '' | Out-Null
  New-Con $e $RF['RF18'] 'Realisation' '' '' | Out-Null
  $e = New-El $pkgM3 'Aprobar novedad' 'UseCase' '' 'Instancia final: la Dirección aprueba o rechaza; toda novedad la requiere.'; $e.Alias = 'CU24'; $e.Update() | Out-Null; $CU['CU24'] = $e; Add-Obj $dM3 $e 280 545 190 60 | Out-Null
  New-Con $e $RF['RF17'] 'Realisation' '' '' | Out-Null
  New-Con $e $RF['RF18'] 'Realisation' '' '' | Out-Null
  $e = New-El $pkgM3 'Devolver para corrección' 'UseCase' '' 'En cualquier instancia la novedad vuelve al solicitante con comentarios.'; $e.Alias = 'CU25'; $e.Update() | Out-Null; $CU['CU25'] = $e; Add-Obj $dM3 $e 530 450 190 60 | Out-Null
  New-Con $e $RF['RF17'] 'Realisation' '' '' | Out-Null
  $e = New-El $pkgM3 'Aplicar novedad al diagrama' 'UseCase' '' 'La novedad aprobada modifica las jornadas afectadas y el saldo de horas del agente.'; $e.Alias = 'CU26'; $e.Update() | Out-Null; $CU['CU26'] = $e; Add-Obj $dM3 $e 530 545 190 60 | Out-Null
  New-Con $e $RF['RF20'] 'Realisation' '' '' | Out-Null
  $e = New-El $pkgM3 'Notificar resultado' 'UseCase' '' 'Cada cambio de estado se notifica al agente y a la jefatura en la aplicación y por correo.'; $e.Alias = 'CU27'; $e.Update() | Out-Null; $CU['CU27'] = $e; Add-Obj $dM3 $e 530 165 190 60 | Out-Null
  New-Con $e $RF['RF19'] 'Realisation' '' '' | Out-Null
  $e = New-El $pkgM3 'Consultar trazabilidad de la novedad' 'UseCase' '' 'Historial de instancias con usuario, fecha, resultado y fundamento.'; $e.Alias = 'CU28'; $e.Update() | Out-Null; $CU['CU28'] = $e; Add-Obj $dM3 $e 280 165 190 60 | Out-Null
  New-Con $e $RF['RF18'] 'Realisation' '' '' | Out-Null
  $e = New-El $pkgM3 'Registrar licencia de origen externo' 'UseCase' '' 'Las licencias tramitadas en los sistemas municipales se asientan como novedades informativas para el parte diario.'; $e.Alias = 'CU29'; $e.Update() | Out-Null; $CU['CU29'] = $e; Add-Obj $dM3 $e 280 450 190 60 | Out-Null
  New-Con $e $RF['RF20'] 'Realisation' '' '' | Out-Null
  Add-Obj $dM3 $ACT['AGENTE'] 60 90 90 90 | Out-Null
  Add-Obj $dM3 $ACT['JS'] 60 240 90 90 | Out-Null
  Add-Obj $dM3 $ACT['OP'] 60 380 90 90 | Out-Null
  Add-Obj $dM3 $ACT['DH'] 60 520 90 90 | Out-Null
  New-Con $ACT['AGENTE'] $CU['CU20'] 'Association' '' '' | Out-Null
  New-Con $ACT['JS'] $CU['CU22'] 'Association' '' '' | Out-Null
  New-Con $ACT['OP'] $CU['CU23'] 'Association' '' '' | Out-Null
  New-Con $ACT['DH'] $CU['CU24'] 'Association' '' '' | Out-Null
  New-Con $ACT['AGENTE'] $CU['CU28'] 'Association' '' '' | Out-Null
  New-Con $ACT['OP'] $CU['CU29'] 'Association' '' '' | Out-Null
  $c = New-Con $CU['CU20'] $CU['CU21'] 'UseCase' 'include' ''; $c.Subtype = 'Includes'; $c.Update() | Out-Null; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null
  $c = New-Con $CU['CU23'] $CU['CU21'] 'UseCase' 'include' ''; $c.Subtype = 'Includes'; $c.Update() | Out-Null; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null
  $c = New-Con $CU['CU25'] $CU['CU22'] 'UseCase' 'extend' ''; $c.Subtype = 'Extends'; $c.Update() | Out-Null; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null
  $c = New-Con $CU['CU25'] $CU['CU23'] 'UseCase' 'extend' ''; $c.Subtype = 'Extends'; $c.Update() | Out-Null; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null
  $c = New-Con $CU['CU25'] $CU['CU24'] 'UseCase' 'extend' ''; $c.Subtype = 'Extends'; $c.Update() | Out-Null; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null
  $c = New-Con $CU['CU24'] $CU['CU26'] 'UseCase' 'include' ''; $c.Subtype = 'Includes'; $c.Update() | Out-Null; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null
  $c = New-Con $CU['CU22'] $CU['CU27'] 'UseCase' 'include' ''; $c.Subtype = 'Includes'; $c.Update() | Out-Null; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null
  $c = New-Con $CU['CU23'] $CU['CU27'] 'UseCase' 'include' ''; $c.Subtype = 'Includes'; $c.Update() | Out-Null; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null
  $c = New-Con $CU['CU24'] $CU['CU27'] 'UseCase' 'include' ''; $c.Subtype = 'Includes'; $c.Update() | Out-Null; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null
  Hide-Links $dM3 $GENIDS
  Export-Png $dM3 'CU-M3-Novedades_y_trazabilidad'
  $pkgM4 = New-Pkg $pkgCU '2.5 M4 Control de asistencia y parte diario'
  $dM4 = New-Dgm $pkgM4 'CU-M4 Control de asistencia y parte diario' 'Use Case' 'Casos de uso del módulo 4: Control de asistencia y parte diario.'
  $bnd = New-El $pkgM4 'Sistema de diagramación de servicios y gestión del ausentismo (HMU)' 'Boundary' '' ''
  $bo = Add-Obj $dM4 $bnd 250 20 730 440; $bo.Sequence = 500; $bo.Update() | Out-Null
  $e = New-El $pkgM4 'Importar marcaciones' 'UseCase' '' 'Carga del archivo exportado por la aplicación de relojes; se asocian las marcaciones a cada agente y se conforman las jornadas registradas.'; $e.Alias = 'CU30'; $e.Update() | Out-Null; $CU['CU30'] = $e; Add-Obj $dM4 $e 280 70 190 60 | Out-Null
  New-Con $e $RF['RF21'] 'Realisation' '' '' | Out-Null
  $e = New-El $pkgM4 'Cotejar asistencia' 'UseCase' '' 'Cruce del diagrama vigente, las jornadas registradas y las novedades aprobadas para cada agente y día.'; $e.Alias = 'CU31'; $e.Update() | Out-Null; $CU['CU31'] = $e; Add-Obj $dM4 $e 530 165 190 60 | Out-Null
  New-Con $e $RF['RF22'] 'Realisation' '' '' | Out-Null
  $e = New-El $pkgM4 'Detectar diferencias' 'UseCase' '' 'Inasistencias, llegadas tarde, retiros anticipados, omisiones de marcación y guardias no cubiertas.'; $e.Alias = 'CU32'; $e.Update() | Out-Null; $CU['CU32'] = $e; Add-Obj $dM4 $e 780 165 190 60 | Out-Null
  New-Con $e $RF['RF22'] 'Realisation' '' '' | Out-Null
  $e = New-El $pkgM4 'Generar parte diario' 'UseCase' '' 'El sistema arma el parte del día a partir del cotejo, con un código por agente y por separado según la vinculación del agente.'; $e.Alias = 'CU33'; $e.Update() | Out-Null; $CU['CU33'] = $e; Add-Obj $dM4 $e 280 165 190 60 | Out-Null
  New-Con $e $RF['RF23'] 'Realisation' '' '' | Out-Null
  $e = New-El $pkgM4 'Revisar y cerrar parte diario' 'UseCase' '' 'La Oficina de Personal completa cargas manuales fundamentadas y cierra el parte con registro del responsable.'; $e.Alias = 'CU34'; $e.Update() | Out-Null; $CU['CU34'] = $e; Add-Obj $dM4 $e 280 260 190 60 | Out-Null
  New-Con $e $RF['RF23'] 'Realisation' '' '' | Out-Null
  $e = New-El $pkgM4 'Solicitar justificación' 'UseCase' '' 'Ante una diferencia sin novedad, se requiere a la jefatura o al agente la presentación de la novedad correspondiente.'; $e.Alias = 'CU35'; $e.Update() | Out-Null; $CU['CU35'] = $e; Add-Obj $dM4 $e 530 260 190 60 | Out-Null
  New-Con $e $RF['RF23'] 'Realisation' '' '' | Out-Null
  $e = New-El $pkgM4 'Consultar indicadores' 'UseCase' '' 'La OP y la DH consultan, por servicio y por período, los indicadores de ausentismo por tipo de novedad y de cobertura, calculados sobre los diagramas aprobados y los partes diarios cerrados.'; $e.Alias = 'CU36'; $e.Update() | Out-Null; $CU['CU36'] = $e; Add-Obj $dM4 $e 280 355 190 60 | Out-Null
  New-Con $e $RF['RF24'] 'Realisation' '' '' | Out-Null
  Add-Obj $dM4 $ACT['OP'] 60 180 90 90 | Out-Null
  Add-Obj $dM4 $ACT['DH'] 60 380 90 90 | Out-Null
  Add-Obj $dM4 $ACT['RELOJ'] 1030 40 100 90 | Out-Null
  Add-Obj $dM4 $ACT['JS'] 1030 230 100 90 | Out-Null
  New-Con $ACT['OP'] $CU['CU30'] 'Association' '' '' | Out-Null
  New-Con $ACT['RELOJ'] $CU['CU30'] 'Association' '' '' | Out-Null
  New-Con $ACT['OP'] $CU['CU33'] 'Association' '' '' | Out-Null
  New-Con $ACT['OP'] $CU['CU34'] 'Association' '' '' | Out-Null
  New-Con $ACT['JS'] $CU['CU35'] 'Association' '' '' | Out-Null
  New-Con $ACT['OP'] $CU['CU36'] 'Association' '' '' | Out-Null
  New-Con $ACT['DH'] $CU['CU36'] 'Association' '' '' | Out-Null
  $c = New-Con $CU['CU33'] $CU['CU31'] 'UseCase' 'include' ''; $c.Subtype = 'Includes'; $c.Update() | Out-Null; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null
  $c = New-Con $CU['CU31'] $CU['CU32'] 'UseCase' 'include' ''; $c.Subtype = 'Includes'; $c.Update() | Out-Null; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null
  $c = New-Con $CU['CU35'] $CU['CU34'] 'UseCase' 'extend' ''; $c.Subtype = 'Extends'; $c.Update() | Out-Null; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null
  Hide-Links $dM4 $GENIDS
  Export-Png $dM4 'CU-M4-Control_de_asistencia_y_parte_diario'

  # ===== 3. Diagramas de secuencia ===========================================
  Log "Paquete Secuencia"
  $pkgDS = New-Pkg $root "3. Diagramas de secuencia"

  $pkgDS01 = New-Pkg $pkgDS 'DS01 Confeccionar, validar y presentar el diagrama mensual'
  $d = New-Dgm $pkgDS01 'DS01 Confeccionar, validar y presentar el diagrama mensual' 'Sequence' 'Casos de uso: CU10, CU11, CU12, CU13, CU14'
  $LL = @{}
  $LL['JS'] = New-El $pkgDS01 'Jefatura de Servicio' 'Actor' '' ''; Add-Obj $d $LL['JS'] 40 40 90 930 | Out-Null
  $LL['UI'] = New-El $pkgDS01 'PantallaDiagramacion' 'Sequence' 'boundary' ''; Add-Obj $d $LL['UI'] 190 40 110 930 | Out-Null
  $LL['SVC'] = New-El $pkgDS01 'DiagramaService' 'Sequence' 'control' ''; Add-Obj $d $LL['SVC'] 340 40 110 930 | Out-Null
  $LL['GEN'] = New-El $pkgDS01 'GeneradorJornadasFactory' 'Sequence' 'control' ''; Add-Obj $d $LL['GEN'] 490 40 110 930 | Out-Null
  $LL['MV'] = New-El $pkgDS01 'MotorValidacion' 'Sequence' 'control' ''; Add-Obj $d $LL['MV'] 640 40 110 930 | Out-Null
  $LL['REP'] = New-El $pkgDS01 'DiagramaRepository' 'Sequence' 'entity' ''; Add-Obj $d $LL['REP'] 790 40 110 930 | Out-Null
  $LL['NOT'] = New-El $pkgDS01 'NotificationService' 'Sequence' 'control' ''; Add-Obj $d $LL['NOT'] 940 40 110 930 | Out-Null
  $c = New-Con $LL['JS'] $LL['UI'] 'Sequence' '' 'seleccionarServicioYPeriodo(servicio, mes)'; $c.SequenceNo = 1; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['UI'] $LL['SVC'] 'Sequence' '' 'crearBorrador(servicioId, desde, hasta, actor)'; $c.SequenceNo = 2; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['SVC'] $LL['REP'] 'Sequence' '' 'findSolapados(servicioId, desde, hasta)'; $c.SequenceNo = 3; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['REP'] $LL['SVC'] 'Sequence' '' ''; $c.SequenceNo = 4; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Return', PDATA2='retval=diagramas existentes (ninguno);' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['SVC'] $LL['GEN'] 'Sequence' '' 'generar(agente, horario, contexto)'; $c.SequenceNo = 5; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call', Btm_Mid_Label='loop por agente' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['GEN'] $LL['SVC'] 'Sequence' '' ''; $c.SequenceNo = 6; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Return', PDATA2='retval=jornadas iniciales;' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['SVC'] $LL['REP'] 'Sequence' '' 'create(diagrama) / reemplazarJornadas(jornadas, 0)'; $c.SequenceNo = 7; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['SVC'] $LL['UI'] 'Sequence' '' ''; $c.SequenceNo = 8; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Return', PDATA2='retval=diagrama BORRADOR (grilla agente x día);' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['JS'] $LL['UI'] 'Sequence' '' 'editarJornada(agente, fecha, tipo, ingreso, egreso)'; $c.SequenceNo = 9; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['UI'] $LL['SVC'] 'Sequence' '' 'guardarJornadas(diagrama, jornadas, actor)'; $c.SequenceNo = 10; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['SVC'] $LL['MV'] 'Sequence' '' 'validar(diagrama, contexto)'; $c.SequenceNo = 11; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['MV'] $LL['MV'] 'Sequence' '' 'evaluarRegla(regla, jornadasAgente)'; $c.SequenceNo = 12; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call', Btm_Mid_Label='loop por regla y agente' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['MV'] $LL['SVC'] 'Sequence' '' ''; $c.SequenceNo = 13; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Return', PDATA2='retval=violaciones (bloqueantes y advertencias);' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['SVC'] $LL['REP'] 'Sequence' '' 'reemplazarJornadas(diagramaId, jornadas, versionEsperada)'; $c.SequenceNo = 14; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['SVC'] $LL['UI'] 'Sequence' '' ''; $c.SequenceNo = 15; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Return', PDATA2='retval=resultado de validación;' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['UI'] $LL['JS'] 'Sequence' '' ''; $c.SequenceNo = 16; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Return', PDATA2='retval=mostrar violaciones por agente y fecha;' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['JS'] $LL['UI'] 'Sequence' '' 'enviarAAprobacion()'; $c.SequenceNo = 17; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['UI'] $LL['SVC'] 'Sequence' '' 'enviarParaAprobacion(diagrama, actor)'; $c.SequenceNo = 18; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['SVC'] $LL['MV'] 'Sequence' '' 'validar(diagrama, contexto)'; $c.SequenceNo = 19; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['MV'] $LL['SVC'] 'Sequence' '' ''; $c.SequenceNo = 20; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Return', PDATA2='retval=sin errores bloqueantes;' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['SVC'] $LL['REP'] 'Sequence' '' 'actualizarEstado(PENDIENTE_APROBACION, versionEsperada)'; $c.SequenceNo = 21; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['REP'] $LL['SVC'] 'Sequence' '' ''; $c.SequenceNo = 22; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Return', PDATA2='retval=ok (asiento de auditoría por disparador);' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['SVC'] $LL['NOT'] 'Sequence' '' 'notificar(OficinaDePersonal, DIAGRAMA_PENDIENTE)'; $c.SequenceNo = 23; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['SVC'] $LL['UI'] 'Sequence' '' ''; $c.SequenceNo = 24; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Return', PDATA2='retval=confirmación;' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['UI'] $LL['JS'] 'Sequence' '' ''; $c.SequenceNo = 25; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Return', PDATA2='retval=mensaje: Diagrama presentado;' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  Export-Png $d 'DS01-secuencia'
  $pkgDS02 = New-Pkg $pkgDS 'DS02 Solicitar y autorizar una novedad con trazabilidad'
  $d = New-Dgm $pkgDS02 'DS02 Solicitar y autorizar una novedad con trazabilidad' 'Sequence' 'Casos de uso: CU20, CU21, CU22, CU23, CU24, CU26, CU27'
  $LL = @{}
  $LL['AG'] = New-El $pkgDS02 'Agente' 'Actor' '' ''; Add-Obj $d $LL['AG'] 40 40 90 930 | Out-Null
  $LL['JS'] = New-El $pkgDS02 'Jefatura de Servicio' 'Actor' '' ''; Add-Obj $d $LL['JS'] 190 40 90 930 | Out-Null
  $LL['OP'] = New-El $pkgDS02 'Oficina de Personal' 'Actor' '' ''; Add-Obj $d $LL['OP'] 340 40 90 930 | Out-Null
  $LL['DH'] = New-El $pkgDS02 'Dirección Hospitalaria' 'Actor' '' ''; Add-Obj $d $LL['DH'] 490 40 90 930 | Out-Null
  $LL['UI'] = New-El $pkgDS02 'PantallaNovedades' 'Sequence' 'boundary' ''; Add-Obj $d $LL['UI'] 640 40 110 930 | Out-Null
  $LL['SVC'] = New-El $pkgDS02 'NovedadService' 'Sequence' 'control' ''; Add-Obj $d $LL['SVC'] 790 40 110 930 | Out-Null
  $LL['MV'] = New-El $pkgDS02 'MotorValidacion' 'Sequence' 'control' ''; Add-Obj $d $LL['MV'] 940 40 110 930 | Out-Null
  $LL['NREP'] = New-El $pkgDS02 'NovedadRepository' 'Sequence' 'entity' ''; Add-Obj $d $LL['NREP'] 1090 40 110 930 | Out-Null
  $LL['DREP'] = New-El $pkgDS02 'DiagramaRepository' 'Sequence' 'entity' ''; Add-Obj $d $LL['DREP'] 1240 40 110 930 | Out-Null
  $LL['NOT'] = New-El $pkgDS02 'NotificationService' 'Sequence' 'control' ''; Add-Obj $d $LL['NOT'] 1390 40 110 930 | Out-Null
  $c = New-Con $LL['AG'] $LL['UI'] 'Sequence' '' 'cargarSolicitud(tipo, fechas, horas, contraparte, motivo, justificacion, adjunto)'; $c.SequenceNo = 1; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['UI'] $LL['SVC'] 'Sequence' '' 'solicitar(novedad, actor)'; $c.SequenceNo = 2; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['SVC'] $LL['MV'] 'Sequence' '' 'validar(novedad, diagramaVigente, bancoHoras)'; $c.SequenceNo = 3; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['MV'] $LL['SVC'] 'Sequence' '' ''; $c.SequenceNo = 4; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Return', PDATA2='retval=resultado (sin errores bloqueantes);' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['SVC'] $LL['NREP'] 'Sequence' '' 'create(novedad, instancias pendientes JS - OP - DH)'; $c.SequenceNo = 5; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['SVC'] $LL['NOT'] 'Sequence' '' 'notificar(jefatura, NOVEDAD_PENDIENTE)'; $c.SequenceNo = 6; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['SVC'] $LL['UI'] 'Sequence' '' ''; $c.SequenceNo = 7; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Return', PDATA2='retval=novedad PENDIENTE_JEFATURA;' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['JS'] $LL['UI'] 'Sequence' '' 'resolver(novedad, AUTORIZADO, comentarios)'; $c.SequenceNo = 8; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['UI'] $LL['SVC'] 'Sequence' '' 'autorizar(novedad, JEFATURA, actor, comentarios)'; $c.SequenceNo = 9; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['SVC'] $LL['NREP'] 'Sequence' '' 'registrarAutorizacion(JEFATURA, actor, fechaHora, comentarios)'; $c.SequenceNo = 10; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['SVC'] $LL['NOT'] 'Sequence' '' 'notificar(OficinaDePersonal, NOVEDAD_PENDIENTE_VERIFICACION)'; $c.SequenceNo = 11; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['OP'] $LL['UI'] 'Sequence' '' 'verificar(novedad)'; $c.SequenceNo = 12; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['UI'] $LL['SVC'] 'Sequence' '' 'verificar(novedad, OFICINA_PERSONAL, actor)'; $c.SequenceNo = 13; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['SVC'] $LL['MV'] 'Sequence' '' 'validar(novedad)'; $c.SequenceNo = 14; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call', Btm_Mid_Label='control formal: códigos, saldos, descanso' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['MV'] $LL['SVC'] 'Sequence' '' ''; $c.SequenceNo = 15; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Return', PDATA2='retval=resultado;' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['SVC'] $LL['NREP'] 'Sequence' '' 'registrarAutorizacion(OFICINA_PERSONAL, actor, fechaHora, comentarios)'; $c.SequenceNo = 16; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['SVC'] $LL['NOT'] 'Sequence' '' 'notificar(Direccion, NOVEDAD_PENDIENTE_APROBACION)'; $c.SequenceNo = 17; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['DH'] $LL['UI'] 'Sequence' '' 'aprobar(novedad)'; $c.SequenceNo = 18; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['UI'] $LL['SVC'] 'Sequence' '' 'aprobar(novedad, DIRECCION, actor)'; $c.SequenceNo = 19; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['SVC'] $LL['NREP'] 'Sequence' '' 'registrarAutorizacion(DIRECCION, ...) y estado APROBADA'; $c.SequenceNo = 20; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['SVC'] $LL['DREP'] 'Sequence' '' 'aplicarNovedad(jornadas afectadas, novedadId)'; $c.SequenceNo = 21; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['SVC'] $LL['NREP'] 'Sequence' '' 'registrarMovimientosHoras(agente, horas): crédito, o débitos FIFO'; $c.SequenceNo = 22; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call', Btm_Mid_Label='si corresponde' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['SVC'] $LL['NOT'] 'Sequence' '' 'notificar(agente y jefatura, NOVEDAD_APROBADA)'; $c.SequenceNo = 23; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['SVC'] $LL['UI'] 'Sequence' '' ''; $c.SequenceNo = 24; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Return', PDATA2='retval=novedad APLICADA;' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['UI'] $LL['AG'] 'Sequence' '' ''; $c.SequenceNo = 25; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Return', PDATA2='retval=historial de instancias (trazabilidad);' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  Export-Png $d 'DS02-secuencia'
  $pkgDS03 = New-Pkg $pkgDS 'DS03 Cotejar la asistencia y generar el parte diario'
  $d = New-Dgm $pkgDS03 'DS03 Cotejar la asistencia y generar el parte diario' 'Sequence' 'Casos de uso: CU30, CU31, CU32, CU33, CU34'
  $LL = @{}
  $LL['OP'] = New-El $pkgDS03 'Oficina de Personal' 'Actor' '' ''; Add-Obj $d $LL['OP'] 40 40 90 998 | Out-Null
  $LL['UI'] = New-El $pkgDS03 'PantallaParteDiario' 'Sequence' 'boundary' ''; Add-Obj $d $LL['UI'] 190 40 110 998 | Out-Null
  $LL['SVC'] = New-El $pkgDS03 'ParteDiarioService' 'Sequence' 'control' ''; Add-Obj $d $LL['SVC'] 340 40 110 998 | Out-Null
  $LL['MREP'] = New-El $pkgDS03 'MarcacionRepository' 'Sequence' 'entity' ''; Add-Obj $d $LL['MREP'] 490 40 110 998 | Out-Null
  $LL['COT'] = New-El $pkgDS03 'CotejoAsistencia' 'Sequence' 'control' ''; Add-Obj $d $LL['COT'] 640 40 110 998 | Out-Null
  $LL['DREP'] = New-El $pkgDS03 'DiagramaRepository' 'Sequence' 'entity' ''; Add-Obj $d $LL['DREP'] 790 40 110 998 | Out-Null
  $LL['NREP'] = New-El $pkgDS03 'NovedadRepository' 'Sequence' 'entity' ''; Add-Obj $d $LL['NREP'] 940 40 110 998 | Out-Null
  $LL['PREP'] = New-El $pkgDS03 'ParteDiarioRepository' 'Sequence' 'entity' ''; Add-Obj $d $LL['PREP'] 1090 40 110 998 | Out-Null
  $LL['NOT'] = New-El $pkgDS03 'NotificationService' 'Sequence' 'control' ''; Add-Obj $d $LL['NOT'] 1240 40 110 998 | Out-Null
  $c = New-Con $LL['OP'] $LL['UI'] 'Sequence' '' 'importarMarcaciones(archivo)'; $c.SequenceNo = 1; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['UI'] $LL['SVC'] 'Sequence' '' 'importar(archivo, actor)'; $c.SequenceNo = 2; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['SVC'] $LL['MREP'] 'Sequence' '' 'guardarLote(lote, marcaciones)'; $c.SequenceNo = 3; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['SVC'] $LL['MREP'] 'Sequence' '' 'conformarJornadasRegistradas(rango)'; $c.SequenceNo = 4; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['MREP'] $LL['SVC'] 'Sequence' '' ''; $c.SequenceNo = 5; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Return', PDATA2='retval=resumen (registros, omisiones);' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['SVC'] $LL['UI'] 'Sequence' '' ''; $c.SequenceNo = 6; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Return', PDATA2='retval=resumen de importación;' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['OP'] $LL['UI'] 'Sequence' '' 'generarParteDiario(fecha)'; $c.SequenceNo = 7; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['UI'] $LL['SVC'] 'Sequence' '' 'generar(fecha, actor)'; $c.SequenceNo = 8; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['SVC'] $LL['DREP'] 'Sequence' '' 'jornadasDiagramadas(fecha)'; $c.SequenceNo = 9; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['DREP'] $LL['SVC'] 'Sequence' '' ''; $c.SequenceNo = 10; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Return', PDATA2='retval=jornadas de todos los servicios;' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['SVC'] $LL['MREP'] 'Sequence' '' 'jornadasRegistradas(fecha)'; $c.SequenceNo = 11; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['MREP'] $LL['SVC'] 'Sequence' '' ''; $c.SequenceNo = 12; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Return', PDATA2='retval=jornadas registradas;' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['SVC'] $LL['NREP'] 'Sequence' '' 'novedadesAprobadas(fecha) y licencias externas'; $c.SequenceNo = 13; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['NREP'] $LL['SVC'] 'Sequence' '' ''; $c.SequenceNo = 14; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Return', PDATA2='retval=novedades;' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['SVC'] $LL['COT'] 'Sequence' '' 'cotejar(diagramadas, registradas, novedades)'; $c.SequenceNo = 15; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['COT'] $LL['COT'] 'Sequence' '' 'detectarDiferencias(agente)'; $c.SequenceNo = 16; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call', Btm_Mid_Label='loop por agente' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['COT'] $LL['SVC'] 'Sequence' '' ''; $c.SequenceNo = 17; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Return', PDATA2='retval=detalles con código por agente;' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['SVC'] $LL['PREP'] 'Sequence' '' 'crear(parteDiario, detalles)'; $c.SequenceNo = 18; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['SVC'] $LL['UI'] 'Sequence' '' ''; $c.SequenceNo = 19; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Return', PDATA2='retval=parte diario ABIERTO;' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['OP'] $LL['UI'] 'Sequence' '' 'cargarDetalleManual(agente, código, observación)'; $c.SequenceNo = 20; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['UI'] $LL['SVC'] 'Sequence' '' 'agregarDetalle(parte, detalle, actor)'; $c.SequenceNo = 21; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['SVC'] $LL['PREP'] 'Sequence' '' 'guardarDetalle(detalle)'; $c.SequenceNo = 22; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['OP'] $LL['UI'] 'Sequence' '' 'cerrarParte()'; $c.SequenceNo = 23; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['UI'] $LL['SVC'] 'Sequence' '' 'cerrar(parteDiario, actor)'; $c.SequenceNo = 24; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['SVC'] $LL['PREP'] 'Sequence' '' 'actualizarEstado(CERRADO, cerradoPor, fechaCierre, version)'; $c.SequenceNo = 25; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['SVC'] $LL['NOT'] 'Sequence' '' 'notificar(jefaturas con diferencias sin justificar)'; $c.SequenceNo = 26; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Call' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  $c = New-Con $LL['SVC'] $LL['UI'] 'Sequence' '' ''; $c.SequenceNo = 27; $c.Update() | Out-Null
  try { $rep.Execute("UPDATE t_connector SET PDATA3='Return', PDATA2='retval=parte CERRADO;' WHERE Connector_ID=" + $c.ConnectorID) } catch { Log ('msg: ' + $_.Exception.Message) }
  Export-Png $d 'DS03-secuencia'

  # ===== 4. Diagrama de clases ===============================================
  Log "Paquete Clases"
  $pkgCL = New-Pkg $root "4. Diagrama de clases"
  $CL = @{}

  $e = New-El $pkgCL 'Usuario' 'Class' '' ''
  $a = $e.Attributes.AddNew('id', 'UUID'); $a.Visibility = 'Private'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('cuil', 'long'); $a.Visibility = 'Private'; $a.Pos = 1; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('apellidos', 'String'); $a.Visibility = 'Private'; $a.Pos = 2; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('nombres', 'String'); $a.Visibility = 'Private'; $a.Pos = 3; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('sexo', 'Sexo'); $a.Visibility = 'Private'; $a.Pos = 4; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('mail', 'String'); $a.Visibility = 'Private'; $a.Pos = 5; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('tel', 'long'); $a.Visibility = 'Private'; $a.Pos = 6; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('fechaAlta', 'LocalDate'); $a.Visibility = 'Private'; $a.Pos = 7; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('estado', 'boolean'); $a.Visibility = 'Private'; $a.Pos = 8; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('passwordHash', 'String'); $a.Visibility = 'Private'; $a.Pos = 9; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('intentosFallidos', 'int'); $a.Visibility = 'Private'; $a.Pos = 10; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('bloqueadoHasta', 'LocalDateTime'); $a.Visibility = 'Private'; $a.Pos = 11; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('cambioPasswordRequerido', 'boolean'); $a.Visibility = 'Private'; $a.Pos = 12; $a.Update() | Out-Null
  $m = $e.Methods.AddNew('validatePassword', 'boolean'); $m.Visibility = 'Public'; $m.Pos = 0; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('raw', 'char[]'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('changePassword', 'boolean'); $m.Visibility = 'Public'; $m.Pos = 1; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('actual', 'char[]'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('nueva', 'char[]'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('hasRole', 'boolean'); $m.Visibility = 'Public'; $m.Pos = 2; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('rol', 'TipoRol'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('fecha', 'LocalDate'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $CL['Usuario'] = $e
  $e = New-El $pkgCL 'Agente' 'Class' '' ''
  $a = $e.Attributes.AddNew('legajo', 'int'); $a.Visibility = 'Private'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('veDiagramaCompleto', 'boolean'); $a.Visibility = 'Private'; $a.Pos = 1; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('saldoHoras', 'BigDecimal'); $a.Visibility = 'Private'; $a.Pos = 2; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('vinculacion', 'TipoVinculacion'); $a.Visibility = 'Private'; $a.Pos = 3; $a.Update() | Out-Null
  $m = $e.Methods.AddNew('consultarDiagrama', 'List<JornadaLaboral>'); $m.Visibility = 'Public'; $m.Pos = 0; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('periodo', 'YearMonth'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('solicitarNovedad', 'void'); $m.Visibility = 'Public'; $m.Pos = 1; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('novedad', 'Novedad'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $CL['Agente'] = $e
  $e = New-El $pkgCL 'Domicilio' 'Class' '' ''
  $a = $e.Attributes.AddNew('id', 'UUID'); $a.Visibility = 'Private'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('calle', 'String'); $a.Visibility = 'Private'; $a.Pos = 1; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('numeracion', 'int'); $a.Visibility = 'Private'; $a.Pos = 2; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('barrio', 'String'); $a.Visibility = 'Private'; $a.Pos = 3; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('ciudad', 'String'); $a.Visibility = 'Private'; $a.Pos = 4; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('provincia', 'String'); $a.Visibility = 'Private'; $a.Pos = 5; $a.Update() | Out-Null
  $CL['Domicilio'] = $e
  $e = New-El $pkgCL 'Cargo' 'Class' '' ''
  $a = $e.Attributes.AddNew('id', 'UUID'); $a.Visibility = 'Private'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('numero', 'int'); $a.Visibility = 'Private'; $a.Pos = 1; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('descripcion', 'String'); $a.Visibility = 'Private'; $a.Pos = 2; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('agrupacion', 'Agrupacion'); $a.Visibility = 'Private'; $a.Pos = 3; $a.Update() | Out-Null
  $CL['Cargo'] = $e
  $e = New-El $pkgCL 'Servicio' 'Class' '' ''
  $a = $e.Attributes.AddNew('id', 'UUID'); $a.Visibility = 'Private'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('nombre', 'String'); $a.Visibility = 'Private'; $a.Pos = 1; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('agrupacion', 'AgrupacionServicio'); $a.Visibility = 'Private'; $a.Pos = 2; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('dotacionMinima', 'int'); $a.Visibility = 'Private'; $a.Pos = 3; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('activo', 'boolean'); $a.Visibility = 'Private'; $a.Pos = 4; $a.Update() | Out-Null
  $m = $e.Methods.AddNew('jefaturaVigente', 'Agente'); $m.Visibility = 'Public'; $m.Pos = 0; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('fecha', 'LocalDate'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('agentesActivos', 'List<Agente>'); $m.Visibility = 'Public'; $m.Pos = 1; $m.Update() | Out-Null
  $CL['Servicio'] = $e
  $e = New-El $pkgCL 'Rol' 'Class' '' ''
  $a = $e.Attributes.AddNew('id', 'UUID'); $a.Visibility = 'Private'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('nombre', 'TipoRol'); $a.Visibility = 'Private'; $a.Pos = 1; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('descripcion', 'String'); $a.Visibility = 'Private'; $a.Pos = 2; $a.Update() | Out-Null
  $CL['Rol'] = $e
  $e = New-El $pkgCL 'AsignacionRol' 'Class' '' ''
  $a = $e.Attributes.AddNew('id', 'UUID'); $a.Visibility = 'Private'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('fechaDesde', 'LocalDate'); $a.Visibility = 'Private'; $a.Pos = 1; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('fechaHasta', 'LocalDate'); $a.Visibility = 'Private'; $a.Pos = 2; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('memorandoRef', 'String'); $a.Visibility = 'Private'; $a.Pos = 3; $a.Update() | Out-Null
  $m = $e.Methods.AddNew('vigenteEn', 'boolean'); $m.Visibility = 'Public'; $m.Pos = 0; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('fecha', 'LocalDate'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $CL['AsignacionRol'] = $e
  $e = New-El $pkgCL 'TipoRol' 'Enumeration' '' ''
  $a = $e.Attributes.AddNew('AGENTE', ''); $a.Stereotype = 'enum'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('JEFATURA_SERVICIO', ''); $a.Stereotype = 'enum'; $a.Pos = 1; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('OFICINA_PERSONAL', ''); $a.Stereotype = 'enum'; $a.Pos = 2; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('DIRECCION', ''); $a.Stereotype = 'enum'; $a.Pos = 3; $a.Update() | Out-Null
  $CL['TipoRol'] = $e
  $e = New-El $pkgCL 'Sexo' 'Enumeration' '' ''
  $a = $e.Attributes.AddNew('FEMENINO', ''); $a.Stereotype = 'enum'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('MASCULINO', ''); $a.Stereotype = 'enum'; $a.Pos = 1; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('OTRO', ''); $a.Stereotype = 'enum'; $a.Pos = 2; $a.Update() | Out-Null
  $CL['Sexo'] = $e
  $e = New-El $pkgCL 'Agrupacion' 'Enumeration' '' ''
  $a = $e.Attributes.AddNew('PLANTAPOLITICA', ''); $a.Stereotype = 'enum'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('JEFATURA', ''); $a.Stereotype = 'enum'; $a.Pos = 1; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('TECNICO', ''); $a.Stereotype = 'enum'; $a.Pos = 2; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('ADMINISTRATIVO', ''); $a.Stereotype = 'enum'; $a.Pos = 3; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('SERVICIO', ''); $a.Stereotype = 'enum'; $a.Pos = 4; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('ENFERMERIA', ''); $a.Stereotype = 'enum'; $a.Pos = 5; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('MEDICO', ''); $a.Stereotype = 'enum'; $a.Pos = 6; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('PROFESIONAL', ''); $a.Stereotype = 'enum'; $a.Pos = 7; $a.Update() | Out-Null
  $CL['Agrupacion'] = $e
  $e = New-El $pkgCL 'TipoVinculacion' 'Enumeration' '' ''
  $a = $e.Attributes.AddNew('PLANTA_PERMANENTE', ''); $a.Stereotype = 'enum'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('PLANTA_TRANSITORIA', ''); $a.Stereotype = 'enum'; $a.Pos = 1; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('CONTRATADO', ''); $a.Stereotype = 'enum'; $a.Pos = 2; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('FUNCIONARIO', ''); $a.Stereotype = 'enum'; $a.Pos = 3; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('MONOTRIBUTISTA', ''); $a.Stereotype = 'enum'; $a.Pos = 4; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('BECARIO', ''); $a.Stereotype = 'enum'; $a.Pos = 5; $a.Update() | Out-Null
  $CL['TipoVinculacion'] = $e
  $e = New-El $pkgCL 'LoginService' 'Class' 'servicio' ''
  $m = $e.Methods.AddNew('autenticar', 'Sesion'); $m.Visibility = 'Public'; $m.Pos = 0; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('cuil', 'long'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('password', 'char[]'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('bloquearCuenta', 'void'); $m.Visibility = 'Public'; $m.Pos = 1; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('usuario', 'Usuario'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('cerrarSesion', 'void'); $m.Visibility = 'Public'; $m.Pos = 2; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('sesion', 'Sesion'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $CL['LoginService'] = $e
  $e = New-El $pkgCL 'UsuarioService' 'Class' 'servicio' ''
  $m = $e.Methods.AddNew('crearAgente', 'Agente'); $m.Visibility = 'Public'; $m.Pos = 0; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('agente', 'Agente'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('darDeBaja', 'void'); $m.Visibility = 'Public'; $m.Pos = 1; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('agente', 'Agente'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('actor', 'Usuario'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('asignarRol', 'AsignacionRol'); $m.Visibility = 'Public'; $m.Pos = 2; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('usuario', 'Usuario'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('rol', 'Rol'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('desde', 'LocalDate'); $p.Kind = 'in'; $p.Position = 2; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('hasta', 'LocalDate'); $p.Kind = 'in'; $p.Position = 3; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('memorando', 'String'); $p.Kind = 'in'; $p.Position = 4; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('revocarRol', 'void'); $m.Visibility = 'Public'; $m.Pos = 3; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('asignacion', 'AsignacionRol'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('actor', 'Usuario'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('asignarHorario', 'void'); $m.Visibility = 'Public'; $m.Pos = 4; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('agente', 'Agente'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('horario', 'HorarioBase'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('importarAgentes', 'List<String>'); $m.Visibility = 'Public'; $m.Pos = 5; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('archivo', 'File'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('actor', 'Usuario'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $CL['UsuarioService'] = $e
  $e = New-El $pkgCL 'ServicioService' 'Class' 'servicio' ''
  $m = $e.Methods.AddNew('crear', 'Servicio'); $m.Visibility = 'Public'; $m.Pos = 0; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('servicio', 'Servicio'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('designarJefatura', 'AsignacionRol'); $m.Visibility = 'Public'; $m.Pos = 1; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('servicio', 'Servicio'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('agente', 'Agente'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('desde', 'LocalDate'); $p.Kind = 'in'; $p.Position = 2; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('memorando', 'String'); $p.Kind = 'in'; $p.Position = 3; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('actualizarDotacion', 'void'); $m.Visibility = 'Public'; $m.Pos = 2; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('servicio', 'Servicio'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('dotacion', 'int'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $CL['ServicioService'] = $e
  $e = New-El $pkgCL 'UsuarioRepository' 'Class' 'repositorio' ''
  $m = $e.Methods.AddNew('findByCuil', 'Usuario'); $m.Visibility = 'Public'; $m.Pos = 0; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('cuil', 'long'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('findAgentesByServicio', 'List<Agente>'); $m.Visibility = 'Public'; $m.Pos = 1; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('servicioId', 'UUID'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('save', 'void'); $m.Visibility = 'Public'; $m.Pos = 2; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('usuario', 'Usuario'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $CL['UsuarioRepository'] = $e
  $e = New-El $pkgCL 'Sesion' 'Class' '' ''
  $a = $e.Attributes.AddNew('usuario', 'Usuario'); $a.Visibility = 'Private'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('rolesVigentes', 'Set<TipoRol>'); $a.Visibility = 'Private'; $a.Pos = 1; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('inicio', 'LocalDateTime'); $a.Visibility = 'Private'; $a.Pos = 2; $a.Update() | Out-Null
  $CL['Sesion'] = $e
  $e = New-El $pkgCL 'HorarioBase' 'Interface' '' ''
  $m = $e.Methods.AddNew('calcularJornadas', 'List<JornadaLaboral>'); $m.Visibility = 'Public'; $m.Pos = 0; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('periodo', 'YearMonth'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('contexto', 'ContextoDiagramacion'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('verificarCondicionesGenerales', 'boolean'); $m.Visibility = 'Public'; $m.Pos = 1; $m.Update() | Out-Null
  $CL['HorarioBase'] = $e
  $e = New-El $pkgCL 'Horario' 'Class' '' ''
  $e.Abstract = '1'; $e.Update() | Out-Null
  $a = $e.Attributes.AddNew('id', 'UUID'); $a.Visibility = 'Private'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('modalidad', 'String'); $a.Visibility = 'Private'; $a.Pos = 1; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('fechaIngreso', 'LocalDateTime'); $a.Visibility = 'Private'; $a.Pos = 2; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('fechaEgreso', 'LocalDateTime'); $a.Visibility = 'Private'; $a.Pos = 3; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('reglasHorario', 'String'); $a.Visibility = 'Private'; $a.Pos = 4; $a.Update() | Out-Null
  $m = $e.Methods.AddNew('verificarCondicionesEspecificas', 'boolean'); $m.Visibility = 'Public'; $m.Pos = 0; $m.Update() | Out-Null
  $CL['Horario'] = $e
  $e = New-El $pkgCL 'HorarioConFranquicia' 'Class' '' ''
  $a = $e.Attributes.AddNew('id', 'UUID'); $a.Visibility = 'Private'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('horasFranquicia', 'int'); $a.Visibility = 'Private'; $a.Pos = 1; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('fechaIngreso', 'LocalDateTime'); $a.Visibility = 'Private'; $a.Pos = 2; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('fechaEgreso', 'LocalDateTime'); $a.Visibility = 'Private'; $a.Pos = 3; $a.Update() | Out-Null
  $m = $e.Methods.AddNew('calcularHorasMensuales', 'int'); $m.Visibility = 'Public'; $m.Pos = 0; $m.Update() | Out-Null
  $CL['HorarioConFranquicia'] = $e
  $e = New-El $pkgCL 'HorarioEstandar' 'Class' '' ''
  $a = $e.Attributes.AddNew('diasLaborables', 'List<DiaSemana>'); $a.Visibility = 'Private'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('horasPorDia', 'int'); $a.Visibility = 'Private'; $a.Pos = 1; $a.Update() | Out-Null
  $CL['HorarioEstandar'] = $e
  $e = New-El $pkgCL 'HorarioSemanal' 'Class' '' ''
  $a = $e.Attributes.AddNew('distribucionSemanal', 'Map<DiaSemana,Integer>'); $a.Visibility = 'Private'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('horaInicioPorDia', 'Map<DiaSemana,LocalTime>'); $a.Visibility = 'Private'; $a.Pos = 1; $a.Update() | Out-Null
  $CL['HorarioSemanal'] = $e
  $e = New-El $pkgCL 'HorarioNocturno' 'Class' '' ''
  $a = $e.Attributes.AddNew('duracionJornadaHoras', 'int'); $a.Visibility = 'Private'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('numeroJornadasMensuales', 'int'); $a.Visibility = 'Private'; $a.Pos = 1; $a.Update() | Out-Null
  $CL['HorarioNocturno'] = $e
  $e = New-El $pkgCL 'HorarioFeriante' 'Class' '' ''
  $a = $e.Attributes.AddNew('duracionGuardiaHoras', 'int'); $a.Visibility = 'Private'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('guardiasMinimasMensuales', 'int'); $a.Visibility = 'Private'; $a.Pos = 1; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('horasMinimasMensuales', 'int'); $a.Visibility = 'Private'; $a.Pos = 2; $a.Update() | Out-Null
  $CL['HorarioFeriante'] = $e
  $e = New-El $pkgCL 'HorarioGuardiaMedica' 'Class' '' ''
  $a = $e.Attributes.AddNew('duracionGuardiaHoras', 'int'); $a.Visibility = 'Private'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('numeroGuardiasSemanal', 'int'); $a.Visibility = 'Private'; $a.Pos = 1; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('tiempoDescansoMinimoHoras', 'int'); $a.Visibility = 'Private'; $a.Pos = 2; $a.Update() | Out-Null
  $CL['HorarioGuardiaMedica'] = $e
  $e = New-El $pkgCL 'HorarioGuardiaEnfermeria' 'Class' '' ''
  $a = $e.Attributes.AddNew('numeroGuardias12Horas', 'int'); $a.Visibility = 'Private'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('numeroGuardias10Horas', 'int'); $a.Visibility = 'Private'; $a.Pos = 1; $a.Update() | Out-Null
  $CL['HorarioGuardiaEnfermeria'] = $e
  $e = New-El $pkgCL 'HorarioDXI' 'Class' '' ''
  $a = $e.Attributes.AddNew('horasSemanales', 'int'); $a.Visibility = 'Private'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('distribucionHoraria', 'Map<DiaSemana,Integer>'); $a.Visibility = 'Private'; $a.Pos = 1; $a.Update() | Out-Null
  $CL['HorarioDXI'] = $e
  $e = New-El $pkgCL 'HorarioJefeServicioGuardiaPasiva' 'Class' '' ''
  $a = $e.Attributes.AddNew('diasLaborables', 'List<DiaSemana>'); $a.Visibility = 'Private'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('horasPorDia', 'int'); $a.Visibility = 'Private'; $a.Pos = 1; $a.Update() | Out-Null
  $CL['HorarioJefeServicioGuardiaPasiva'] = $e
  $e = New-El $pkgCL 'HorarioAbierto' 'Class' '' ''
  $a = $e.Attributes.AddNew('horasSemanales', 'int'); $a.Visibility = 'Private'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('flexibilidadHoraria', 'boolean'); $a.Visibility = 'Private'; $a.Pos = 1; $a.Update() | Out-Null
  $CL['HorarioAbierto'] = $e
  $e = New-El $pkgCL 'DiagramaDeServicio' 'Class' '' ''
  $a = $e.Attributes.AddNew('id', 'UUID'); $a.Visibility = 'Private'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('estado', 'EstadoDiagrama'); $a.Visibility = 'Private'; $a.Pos = 1; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('fechaInicio', 'LocalDate'); $a.Visibility = 'Private'; $a.Pos = 2; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('fechaFin', 'LocalDate'); $a.Visibility = 'Private'; $a.Pos = 3; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('version', 'int'); $a.Visibility = 'Private'; $a.Pos = 4; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('createdAt', 'LocalDateTime'); $a.Visibility = 'Private'; $a.Pos = 5; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('fechaAprobacion', 'LocalDateTime'); $a.Visibility = 'Private'; $a.Pos = 6; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('comentariosObservacion', 'String'); $a.Visibility = 'Private'; $a.Pos = 7; $a.Update() | Out-Null
  $m = $e.Methods.AddNew('addJornada', 'void'); $m.Visibility = 'Public'; $m.Pos = 0; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('jornada', 'JornadaLaboral'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('jornadasDe', 'List<JornadaLaboral>'); $m.Visibility = 'Public'; $m.Pos = 1; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('agente', 'Agente'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $CL['DiagramaDeServicio'] = $e
  $e = New-El $pkgCL 'JornadaLaboral' 'Class' '' ''
  $a = $e.Attributes.AddNew('id', 'UUID'); $a.Visibility = 'Private'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('fecha', 'LocalDate'); $a.Visibility = 'Private'; $a.Pos = 1; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('tipo', 'TipoJornada'); $a.Visibility = 'Private'; $a.Pos = 2; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('fechaIngreso', 'LocalDateTime'); $a.Visibility = 'Private'; $a.Pos = 3; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('fechaEgreso', 'LocalDateTime'); $a.Visibility = 'Private'; $a.Pos = 4; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('observaciones', 'String'); $a.Visibility = 'Private'; $a.Pos = 5; $a.Update() | Out-Null
  $m = $e.Methods.AddNew('tieneHorario', 'boolean'); $m.Visibility = 'Public'; $m.Pos = 0; $m.Update() | Out-Null
  $m = $e.Methods.AddNew('calcularDuracion', 'Duration'); $m.Visibility = 'Public'; $m.Pos = 1; $m.Update() | Out-Null
  $m = $e.Methods.AddNew('seSuperponeCon', 'boolean'); $m.Visibility = 'Public'; $m.Pos = 2; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('otra', 'JornadaLaboral'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $CL['JornadaLaboral'] = $e
  $e = New-El $pkgCL 'EstadoDiagrama' 'Enumeration' '' ''
  $a = $e.Attributes.AddNew('BORRADOR', ''); $a.Stereotype = 'enum'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('PENDIENTE_APROBACION', ''); $a.Stereotype = 'enum'; $a.Pos = 1; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('APROBADO', ''); $a.Stereotype = 'enum'; $a.Pos = 2; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('OBSERVADO', ''); $a.Stereotype = 'enum'; $a.Pos = 3; $a.Update() | Out-Null
  $CL['EstadoDiagrama'] = $e
  $e = New-El $pkgCL 'TipoJornada' 'Enumeration' '' ''
  $a = $e.Attributes.AddNew('TURNO_NORMAL', ''); $a.Stereotype = 'enum'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('GUARDIA_ACTIVA', ''); $a.Stereotype = 'enum'; $a.Pos = 1; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('GUARDIA_PASIVA', ''); $a.Stereotype = 'enum'; $a.Pos = 2; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('FRANCO', ''); $a.Stereotype = 'enum'; $a.Pos = 3; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('FRANCO_COMPENSATORIO', ''); $a.Stereotype = 'enum'; $a.Pos = 4; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('LICENCIA', ''); $a.Stereotype = 'enum'; $a.Pos = 5; $a.Update() | Out-Null
  $CL['TipoJornada'] = $e
  $e = New-El $pkgCL 'Feriado' 'Class' '' ''
  $a = $e.Attributes.AddNew('id', 'UUID'); $a.Visibility = 'Private'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('fecha', 'LocalDate'); $a.Visibility = 'Private'; $a.Pos = 1; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('descripcion', 'String'); $a.Visibility = 'Private'; $a.Pos = 2; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('estado', 'EstadoFeriado'); $a.Visibility = 'Private'; $a.Pos = 3; $a.Update() | Out-Null
  $m = $e.Methods.AddNew('cuentaComoFeriado', 'boolean'); $m.Visibility = 'Public'; $m.Pos = 0; $m.Update() | Out-Null
  $CL['Feriado'] = $e
  $e = New-El $pkgCL 'ReglaValidacion' 'Class' '' ''
  $a = $e.Attributes.AddNew('id', 'UUID'); $a.Visibility = 'Private'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('codigo', 'String'); $a.Visibility = 'Private'; $a.Pos = 1; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('nombre', 'String'); $a.Visibility = 'Private'; $a.Pos = 2; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('ambito', 'AmbitoRegla'); $a.Visibility = 'Private'; $a.Pos = 3; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('modalidad', 'String'); $a.Visibility = 'Private'; $a.Pos = 4; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('severidad', 'Severidad'); $a.Visibility = 'Private'; $a.Pos = 5; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('parametros', 'Map<String,String>'); $a.Visibility = 'Private'; $a.Pos = 6; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('vigenteDesde', 'LocalDate'); $a.Visibility = 'Private'; $a.Pos = 7; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('vigenteHasta', 'LocalDate'); $a.Visibility = 'Private'; $a.Pos = 8; $a.Update() | Out-Null
  $m = $e.Methods.AddNew('aplicaA', 'boolean'); $m.Visibility = 'Public'; $m.Pos = 0; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('horario', 'Horario'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('fecha', 'LocalDate'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $CL['ReglaValidacion'] = $e
  $e = New-El $pkgCL 'Violacion' 'Class' '' ''
  $a = $e.Attributes.AddNew('regla', 'ReglaValidacion'); $a.Visibility = 'Private'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('agente', 'Agente'); $a.Visibility = 'Private'; $a.Pos = 1; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('fecha', 'LocalDate'); $a.Visibility = 'Private'; $a.Pos = 2; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('severidad', 'Severidad'); $a.Visibility = 'Private'; $a.Pos = 3; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('mensaje', 'String'); $a.Visibility = 'Private'; $a.Pos = 4; $a.Update() | Out-Null
  $m = $e.Methods.AddNew('esBloqueante', 'boolean'); $m.Visibility = 'Public'; $m.Pos = 0; $m.Update() | Out-Null
  $CL['Violacion'] = $e
  $e = New-El $pkgCL 'Severidad' 'Enumeration' '' ''
  $a = $e.Attributes.AddNew('BLOQUEANTE', ''); $a.Stereotype = 'enum'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('ADVERTENCIA', ''); $a.Stereotype = 'enum'; $a.Pos = 1; $a.Update() | Out-Null
  $CL['Severidad'] = $e
  $e = New-El $pkgCL 'AmbitoRegla' 'Enumeration' '' ''
  $a = $e.Attributes.AddNew('DIAGRAMA', ''); $a.Stereotype = 'enum'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('NOVEDAD', ''); $a.Stereotype = 'enum'; $a.Pos = 1; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('AMBOS', ''); $a.Stereotype = 'enum'; $a.Pos = 2; $a.Update() | Out-Null
  $CL['AmbitoRegla'] = $e
  $e = New-El $pkgCL 'MotorValidacion' 'Class' 'servicio' ''
  $m = $e.Methods.AddNew('validarDiagrama', 'List<Violacion>'); $m.Visibility = 'Public'; $m.Pos = 0; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('diagrama', 'DiagramaDeServicio'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('contexto', 'ContextoDiagramacion'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('validarNovedad', 'List<Violacion>'); $m.Visibility = 'Public'; $m.Pos = 1; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('novedad', 'Novedad'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('diagramaVigente', 'DiagramaDeServicio'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('bancoHoras', 'List<MovimientoHoras>'); $p.Kind = 'in'; $p.Position = 2; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('evaluar', 'List<Violacion>'); $m.Visibility = 'Public'; $m.Pos = 2; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('regla', 'ReglaValidacion'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('jornadas', 'List<JornadaLaboral>'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $CL['MotorValidacion'] = $e
  $e = New-El $pkgCL 'ContextoDiagramacion' 'Class' '' ''
  $a = $e.Attributes.AddNew('feriados', 'Set<LocalDate>'); $a.Visibility = 'Private'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('periodo', 'YearMonth'); $a.Visibility = 'Private'; $a.Pos = 1; $a.Update() | Out-Null
  $m = $e.Methods.AddNew('esFeriado', 'boolean'); $m.Visibility = 'Public'; $m.Pos = 0; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('fecha', 'LocalDate'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('esDiaHabil', 'boolean'); $m.Visibility = 'Public'; $m.Pos = 1; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('fecha', 'LocalDate'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $CL['ContextoDiagramacion'] = $e
  $e = New-El $pkgCL 'GeneradorJornadas' 'Interface' '' ''
  $m = $e.Methods.AddNew('generar', 'List<JornadaLaboral>'); $m.Visibility = 'Public'; $m.Pos = 0; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('agente', 'Agente'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('horario', 'Horario'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('contexto', 'ContextoDiagramacion'); $p.Kind = 'in'; $p.Position = 2; $p.Update() | Out-Null
  $CL['GeneradorJornadas'] = $e
  $e = New-El $pkgCL 'GeneradorJornadasFactory' 'Class' 'servicio' ''
  $m = $e.Methods.AddNew('generadorPara', 'GeneradorJornadas'); $m.Visibility = 'Public'; $m.Pos = 0; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('horario', 'HorarioBase'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $CL['GeneradorJornadasFactory'] = $e
  $e = New-El $pkgCL 'GeneradorFranquiciaDecorator' 'Class' 'servicio' ''
  $a = $e.Attributes.AddNew('horasFranquicia', 'int'); $a.Visibility = 'Private'; $a.Pos = 0; $a.Update() | Out-Null
  $m = $e.Methods.AddNew('generar', 'List<JornadaLaboral>'); $m.Visibility = 'Public'; $m.Pos = 0; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('agente', 'Agente'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('horario', 'Horario'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('contexto', 'ContextoDiagramacion'); $p.Kind = 'in'; $p.Position = 2; $p.Update() | Out-Null
  $CL['GeneradorFranquiciaDecorator'] = $e
  $e = New-El $pkgCL 'DiagramaService' 'Class' 'servicio' ''
  $m = $e.Methods.AddNew('crearBorrador', 'DiagramaDeServicio'); $m.Visibility = 'Public'; $m.Pos = 0; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('servicioId', 'UUID'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('desde', 'LocalDate'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('hasta', 'LocalDate'); $p.Kind = 'in'; $p.Position = 2; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('creador', 'Usuario'); $p.Kind = 'in'; $p.Position = 3; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('guardarJornadas', 'List<Violacion>'); $m.Visibility = 'Public'; $m.Pos = 1; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('diagrama', 'DiagramaDeServicio'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('jornadas', 'List<JornadaLaboral>'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('actor', 'Usuario'); $p.Kind = 'in'; $p.Position = 2; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('enviarParaAprobacion', 'void'); $m.Visibility = 'Public'; $m.Pos = 2; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('diagrama', 'DiagramaDeServicio'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('actor', 'Usuario'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('aprobar', 'void'); $m.Visibility = 'Public'; $m.Pos = 3; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('diagrama', 'DiagramaDeServicio'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('aprobador', 'Usuario'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('observar', 'void'); $m.Visibility = 'Public'; $m.Pos = 4; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('diagrama', 'DiagramaDeServicio'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('comentarios', 'String'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('actor', 'Usuario'); $p.Kind = 'in'; $p.Position = 2; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('serviciosSinPresentar', 'List<AlertaPresentacion>'); $m.Visibility = 'Public'; $m.Pos = 5; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('mes', 'YearMonth'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $CL['DiagramaService'] = $e
  $e = New-El $pkgCL 'DiagramaStateMachine' 'Class' 'servicio' ''
  $m = $e.Methods.AddNew('puedeTransicionar', 'boolean'); $m.Visibility = 'Public'; $m.Pos = 0; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('de', 'EstadoDiagrama'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('a', 'EstadoDiagrama'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('transicionar', 'void'); $m.Visibility = 'Public'; $m.Pos = 1; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('diagrama', 'DiagramaDeServicio'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('a', 'EstadoDiagrama'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('actor', 'Usuario'); $p.Kind = 'in'; $p.Position = 2; $p.Update() | Out-Null
  $CL['DiagramaStateMachine'] = $e
  $e = New-El $pkgCL 'DiagramaRepository' 'Class' 'repositorio' ''
  $m = $e.Methods.AddNew('findSolapados', 'List<DiagramaDeServicio>'); $m.Visibility = 'Public'; $m.Pos = 0; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('servicioId', 'UUID'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('desde', 'LocalDate'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('hasta', 'LocalDate'); $p.Kind = 'in'; $p.Position = 2; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('findVigente', 'DiagramaDeServicio'); $m.Visibility = 'Public'; $m.Pos = 1; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('servicioId', 'UUID'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('fecha', 'LocalDate'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('reemplazarJornadas', 'boolean'); $m.Visibility = 'Public'; $m.Pos = 2; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('diagramaId', 'UUID'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('jornadas', 'List<JornadaLaboral>'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('versionEsperada', 'int'); $p.Kind = 'in'; $p.Position = 2; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('actualizarEstado', 'boolean'); $m.Visibility = 'Public'; $m.Pos = 3; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('diagramaId', 'UUID'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('estado', 'EstadoDiagrama'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('actor', 'Usuario'); $p.Kind = 'in'; $p.Position = 2; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('comentarios', 'String'); $p.Kind = 'in'; $p.Position = 3; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('versionEsperada', 'int'); $p.Kind = 'in'; $p.Position = 4; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('jornadasDiagramadas', 'List<JornadaLaboral>'); $m.Visibility = 'Public'; $m.Pos = 4; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('fecha', 'LocalDate'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $CL['DiagramaRepository'] = $e
  $e = New-El $pkgCL 'ReglaRepository' 'Class' 'repositorio' ''
  $m = $e.Methods.AddNew('reglasVigentes', 'List<ReglaValidacion>'); $m.Visibility = 'Public'; $m.Pos = 0; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('ambito', 'AmbitoRegla'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('fecha', 'LocalDate'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $CL['ReglaRepository'] = $e
  $e = New-El $pkgCL 'CodigoNovedad' 'Class' '' ''
  $a = $e.Attributes.AddNew('id', 'UUID'); $a.Visibility = 'Private'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('codigo', 'int'); $a.Visibility = 'Private'; $a.Pos = 1; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('denominacionHmu', 'String'); $a.Visibility = 'Private'; $a.Pos = 2; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('denominacionCapitalHumano', 'String'); $a.Visibility = 'Private'; $a.Pos = 3; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('sigla', 'String'); $a.Visibility = 'Private'; $a.Pos = 4; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('esLicencia', 'boolean'); $a.Visibility = 'Private'; $a.Pos = 5; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('activo', 'boolean'); $a.Visibility = 'Private'; $a.Pos = 6; $a.Update() | Out-Null
  $CL['CodigoNovedad'] = $e
  $e = New-El $pkgCL 'TipoNovedad' 'Class' '' ''
  $a = $e.Attributes.AddNew('id', 'UUID'); $a.Visibility = 'Private'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('codigo', 'String'); $a.Visibility = 'Private'; $a.Pos = 1; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('nombre', 'String'); $a.Visibility = 'Private'; $a.Pos = 2; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('requiereContraparte', 'boolean'); $a.Visibility = 'Private'; $a.Pos = 3; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('afectaSaldoHoras', 'int'); $a.Visibility = 'Private'; $a.Pos = 4; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('requiereDocumentacion', 'boolean'); $a.Visibility = 'Private'; $a.Pos = 5; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('esExterna', 'boolean'); $a.Visibility = 'Private'; $a.Pos = 6; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('activo', 'boolean'); $a.Visibility = 'Private'; $a.Pos = 7; $a.Update() | Out-Null
  $CL['TipoNovedad'] = $e
  $e = New-El $pkgCL 'Novedad' 'Class' '' ''
  $a = $e.Attributes.AddNew('id', 'UUID'); $a.Visibility = 'Private'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('fechaDesde', 'LocalDate'); $a.Visibility = 'Private'; $a.Pos = 1; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('fechaHasta', 'LocalDate'); $a.Visibility = 'Private'; $a.Pos = 2; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('horaDesde', 'LocalTime'); $a.Visibility = 'Private'; $a.Pos = 3; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('horaHasta', 'LocalTime'); $a.Visibility = 'Private'; $a.Pos = 4; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('cantHoras', 'BigDecimal'); $a.Visibility = 'Private'; $a.Pos = 5; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('motivo', 'String'); $a.Visibility = 'Private'; $a.Pos = 6; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('justificacion', 'String'); $a.Visibility = 'Private'; $a.Pos = 7; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('estado', 'EstadoNovedad'); $a.Visibility = 'Private'; $a.Pos = 8; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('origen', 'OrigenNovedad'); $a.Visibility = 'Private'; $a.Pos = 9; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('fechaSolicitud', 'LocalDateTime'); $a.Visibility = 'Private'; $a.Pos = 10; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('referenciaExterna', 'String'); $a.Visibility = 'Private'; $a.Pos = 11; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('version', 'int'); $a.Visibility = 'Private'; $a.Pos = 12; $a.Update() | Out-Null
  $m = $e.Methods.AddNew('instanciaActual', 'NovedadAutorizacion'); $m.Visibility = 'Public'; $m.Pos = 0; $m.Update() | Out-Null
  $m = $e.Methods.AddNew('historial', 'List<NovedadAutorizacion>'); $m.Visibility = 'Public'; $m.Pos = 1; $m.Update() | Out-Null
  $CL['Novedad'] = $e
  $e = New-El $pkgCL 'NovedadAutorizacion' 'Class' '' ''
  $a = $e.Attributes.AddNew('id', 'UUID'); $a.Visibility = 'Private'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('instancia', 'InstanciaAutorizacion'); $a.Visibility = 'Private'; $a.Pos = 1; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('orden', 'int'); $a.Visibility = 'Private'; $a.Pos = 2; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('estado', 'EstadoAutorizacion'); $a.Visibility = 'Private'; $a.Pos = 3; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('fechaResolucion', 'LocalDateTime'); $a.Visibility = 'Private'; $a.Pos = 4; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('comentarios', 'String'); $a.Visibility = 'Private'; $a.Pos = 5; $a.Update() | Out-Null
  $CL['NovedadAutorizacion'] = $e
  $e = New-El $pkgCL 'NovedadAdjunto' 'Class' '' ''
  $a = $e.Attributes.AddNew('id', 'UUID'); $a.Visibility = 'Private'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('nombreArchivo', 'String'); $a.Visibility = 'Private'; $a.Pos = 1; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('contenido', 'byte[]'); $a.Visibility = 'Private'; $a.Pos = 2; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('fechaCarga', 'LocalDateTime'); $a.Visibility = 'Private'; $a.Pos = 3; $a.Update() | Out-Null
  $CL['NovedadAdjunto'] = $e
  $e = New-El $pkgCL 'MovimientoHoras' 'Class' '' ''
  $a = $e.Attributes.AddNew('id', 'UUID'); $a.Visibility = 'Private'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('fecha', 'LocalDate'); $a.Visibility = 'Private'; $a.Pos = 1; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('horas', 'BigDecimal'); $a.Visibility = 'Private'; $a.Pos = 2; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('concepto', 'String'); $a.Visibility = 'Private'; $a.Pos = 3; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('saldoResultante', 'BigDecimal'); $a.Visibility = 'Private'; $a.Pos = 4; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('tipo', 'TipoMovimiento'); $a.Visibility = 'Private'; $a.Pos = 5; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('fechaVencimiento', 'LocalDate'); $a.Visibility = 'Private'; $a.Pos = 6; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('horasRestantes', 'BigDecimal'); $a.Visibility = 'Private'; $a.Pos = 7; $a.Update() | Out-Null
  $CL['MovimientoHoras'] = $e
  $e = New-El $pkgCL 'TipoMovimiento' 'Enumeration' '' ''
  $a = $e.Attributes.AddNew('CREDITO', ''); $a.Stereotype = 'enum'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('DEBITO', ''); $a.Stereotype = 'enum'; $a.Pos = 1; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('VENCIMIENTO', ''); $a.Stereotype = 'enum'; $a.Pos = 2; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('AJUSTE', ''); $a.Stereotype = 'enum'; $a.Pos = 3; $a.Update() | Out-Null
  $CL['TipoMovimiento'] = $e
  $e = New-El $pkgCL 'EstadoNovedad' 'Enumeration' '' ''
  $a = $e.Attributes.AddNew('BORRADOR', ''); $a.Stereotype = 'enum'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('PENDIENTE_JEFATURA', ''); $a.Stereotype = 'enum'; $a.Pos = 1; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('PENDIENTE_OFICINA_PERSONAL', ''); $a.Stereotype = 'enum'; $a.Pos = 2; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('PENDIENTE_DIRECCION', ''); $a.Stereotype = 'enum'; $a.Pos = 3; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('OBSERVADA', ''); $a.Stereotype = 'enum'; $a.Pos = 4; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('APROBADA', ''); $a.Stereotype = 'enum'; $a.Pos = 5; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('APLICADA', ''); $a.Stereotype = 'enum'; $a.Pos = 6; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('RECHAZADA', ''); $a.Stereotype = 'enum'; $a.Pos = 7; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('ANULADA', ''); $a.Stereotype = 'enum'; $a.Pos = 8; $a.Update() | Out-Null
  $CL['EstadoNovedad'] = $e
  $e = New-El $pkgCL 'InstanciaAutorizacion' 'Enumeration' '' ''
  $a = $e.Attributes.AddNew('JEFATURA', ''); $a.Stereotype = 'enum'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('OFICINA_PERSONAL', ''); $a.Stereotype = 'enum'; $a.Pos = 1; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('DIRECCION', ''); $a.Stereotype = 'enum'; $a.Pos = 2; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('SECRETARIA_SALUD', ''); $a.Stereotype = 'enum'; $a.Pos = 3; $a.Update() | Out-Null
  $CL['InstanciaAutorizacion'] = $e
  $e = New-El $pkgCL 'EstadoAutorizacion' 'Enumeration' '' ''
  $a = $e.Attributes.AddNew('PENDIENTE', ''); $a.Stereotype = 'enum'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('AUTORIZADO', ''); $a.Stereotype = 'enum'; $a.Pos = 1; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('RECHAZADO', ''); $a.Stereotype = 'enum'; $a.Pos = 2; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('OBSERVADO', ''); $a.Stereotype = 'enum'; $a.Pos = 3; $a.Update() | Out-Null
  $CL['EstadoAutorizacion'] = $e
  $e = New-El $pkgCL 'OrigenNovedad' 'Enumeration' '' ''
  $a = $e.Attributes.AddNew('SOLICITUD', ''); $a.Stereotype = 'enum'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('COTEJO', ''); $a.Stereotype = 'enum'; $a.Pos = 1; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('LICENCIA_EXTERNA', ''); $a.Stereotype = 'enum'; $a.Pos = 2; $a.Update() | Out-Null
  $CL['OrigenNovedad'] = $e
  $e = New-El $pkgCL 'NovedadService' 'Class' 'servicio' ''
  $m = $e.Methods.AddNew('solicitar', 'Novedad'); $m.Visibility = 'Public'; $m.Pos = 0; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('novedad', 'Novedad'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('actor', 'Usuario'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('autorizar', 'void'); $m.Visibility = 'Public'; $m.Pos = 1; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('novedad', 'Novedad'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('instancia', 'InstanciaAutorizacion'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('actor', 'Usuario'); $p.Kind = 'in'; $p.Position = 2; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('comentarios', 'String'); $p.Kind = 'in'; $p.Position = 3; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('observar', 'void'); $m.Visibility = 'Public'; $m.Pos = 2; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('novedad', 'Novedad'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('instancia', 'InstanciaAutorizacion'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('actor', 'Usuario'); $p.Kind = 'in'; $p.Position = 2; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('comentarios', 'String'); $p.Kind = 'in'; $p.Position = 3; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('rechazar', 'void'); $m.Visibility = 'Public'; $m.Pos = 3; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('novedad', 'Novedad'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('instancia', 'InstanciaAutorizacion'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('actor', 'Usuario'); $p.Kind = 'in'; $p.Position = 2; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('comentarios', 'String'); $p.Kind = 'in'; $p.Position = 3; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('aplicar', 'void'); $m.Visibility = 'Public'; $m.Pos = 4; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('novedad', 'Novedad'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('registrarLicenciaExterna', 'Novedad'); $m.Visibility = 'Public'; $m.Pos = 5; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('agente', 'Agente'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('desde', 'LocalDate'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('hasta', 'LocalDate'); $p.Kind = 'in'; $p.Position = 2; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('referencia', 'String'); $p.Kind = 'in'; $p.Position = 3; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('actor', 'Usuario'); $p.Kind = 'in'; $p.Position = 4; $p.Update() | Out-Null
  $CL['NovedadService'] = $e
  $e = New-El $pkgCL 'NovedadStateMachine' 'Class' 'servicio' ''
  $m = $e.Methods.AddNew('siguienteInstancia', 'InstanciaAutorizacion'); $m.Visibility = 'Public'; $m.Pos = 0; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('novedad', 'Novedad'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('transicionar', 'EstadoNovedad'); $m.Visibility = 'Public'; $m.Pos = 1; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('novedad', 'Novedad'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('resultado', 'EstadoAutorizacion'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $CL['NovedadStateMachine'] = $e
  $e = New-El $pkgCL 'NovedadRepository' 'Class' 'repositorio' ''
  $m = $e.Methods.AddNew('create', 'void'); $m.Visibility = 'Public'; $m.Pos = 0; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('novedad', 'Novedad'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('registrarAutorizacion', 'void'); $m.Visibility = 'Public'; $m.Pos = 1; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('autorizacion', 'NovedadAutorizacion'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('novedadesAprobadas', 'List<Novedad>'); $m.Visibility = 'Public'; $m.Pos = 2; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('fecha', 'LocalDate'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('pendientesPara', 'List<Novedad>'); $m.Visibility = 'Public'; $m.Pos = 3; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('instancia', 'InstanciaAutorizacion'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('usuario', 'Usuario'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('registrarMovimientoHoras', 'void'); $m.Visibility = 'Public'; $m.Pos = 4; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('mov', 'MovimientoHoras'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $CL['NovedadRepository'] = $e
  $e = New-El $pkgCL 'LoteMarcaciones' 'Class' '' ''
  $a = $e.Attributes.AddNew('id', 'UUID'); $a.Visibility = 'Private'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('nombreArchivo', 'String'); $a.Visibility = 'Private'; $a.Pos = 1; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('fechaImportacion', 'LocalDateTime'); $a.Visibility = 'Private'; $a.Pos = 2; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('cantidadRegistros', 'int'); $a.Visibility = 'Private'; $a.Pos = 3; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('rangoDesde', 'LocalDate'); $a.Visibility = 'Private'; $a.Pos = 4; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('rangoHasta', 'LocalDate'); $a.Visibility = 'Private'; $a.Pos = 5; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('hashArchivo', 'String'); $a.Visibility = 'Private'; $a.Pos = 6; $a.Update() | Out-Null
  $CL['LoteMarcaciones'] = $e
  $e = New-El $pkgCL 'Marcacion' 'Class' '' ''
  $a = $e.Attributes.AddNew('id', 'UUID'); $a.Visibility = 'Private'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('fechaHora', 'LocalDateTime'); $a.Visibility = 'Private'; $a.Pos = 1; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('tipo', 'TipoMarcacion'); $a.Visibility = 'Private'; $a.Pos = 2; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('dispositivo', 'String'); $a.Visibility = 'Private'; $a.Pos = 3; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('validada', 'boolean'); $a.Visibility = 'Private'; $a.Pos = 4; $a.Update() | Out-Null
  $m = $e.Methods.AddNew('esIngreso', 'boolean'); $m.Visibility = 'Public'; $m.Pos = 0; $m.Update() | Out-Null
  $m = $e.Methods.AddNew('esEgreso', 'boolean'); $m.Visibility = 'Public'; $m.Pos = 1; $m.Update() | Out-Null
  $CL['Marcacion'] = $e
  $e = New-El $pkgCL 'JornadaRegistrada' 'Class' '' ''
  $a = $e.Attributes.AddNew('id', 'UUID'); $a.Visibility = 'Private'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('fecha', 'LocalDate'); $a.Visibility = 'Private'; $a.Pos = 1; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('duracionMinutos', 'int'); $a.Visibility = 'Private'; $a.Pos = 2; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('omisionIngreso', 'boolean'); $a.Visibility = 'Private'; $a.Pos = 3; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('omisionEgreso', 'boolean'); $a.Visibility = 'Private'; $a.Pos = 4; $a.Update() | Out-Null
  $m = $e.Methods.AddNew('calcularDuracion', 'Duration'); $m.Visibility = 'Public'; $m.Pos = 0; $m.Update() | Out-Null
  $m = $e.Methods.AddNew('tieneOmisiones', 'boolean'); $m.Visibility = 'Public'; $m.Pos = 1; $m.Update() | Out-Null
  $CL['JornadaRegistrada'] = $e
  $e = New-El $pkgCL 'TipoMarcacion' 'Enumeration' '' ''
  $a = $e.Attributes.AddNew('INGRESO', ''); $a.Stereotype = 'enum'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('EGRESO', ''); $a.Stereotype = 'enum'; $a.Pos = 1; $a.Update() | Out-Null
  $CL['TipoMarcacion'] = $e
  $e = New-El $pkgCL 'ParteDiario' 'Class' '' ''
  $a = $e.Attributes.AddNew('id', 'UUID'); $a.Visibility = 'Private'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('fecha', 'LocalDate'); $a.Visibility = 'Private'; $a.Pos = 1; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('estado', 'EstadoParte'); $a.Visibility = 'Private'; $a.Pos = 2; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('generadoEn', 'LocalDateTime'); $a.Visibility = 'Private'; $a.Pos = 3; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('fechaCierre', 'LocalDateTime'); $a.Visibility = 'Private'; $a.Pos = 4; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('version', 'int'); $a.Visibility = 'Private'; $a.Pos = 5; $a.Update() | Out-Null
  $m = $e.Methods.AddNew('agregarDetalle', 'void'); $m.Visibility = 'Public'; $m.Pos = 0; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('detalle', 'ParteDiarioDetalle'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('cerrar', 'void'); $m.Visibility = 'Public'; $m.Pos = 1; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('actor', 'Usuario'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $CL['ParteDiario'] = $e
  $e = New-El $pkgCL 'ParteDiarioDetalle' 'Class' '' ''
  $a = $e.Attributes.AddNew('id', 'UUID'); $a.Visibility = 'Private'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('origen', 'OrigenDetalle'); $a.Visibility = 'Private'; $a.Pos = 1; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('tipoDiferencia', 'TipoDiferencia'); $a.Visibility = 'Private'; $a.Pos = 2; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('observaciones', 'String'); $a.Visibility = 'Private'; $a.Pos = 3; $a.Update() | Out-Null
  $CL['ParteDiarioDetalle'] = $e
  $e = New-El $pkgCL 'EstadoParte' 'Enumeration' '' ''
  $a = $e.Attributes.AddNew('ABIERTO', ''); $a.Stereotype = 'enum'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('CERRADO', ''); $a.Stereotype = 'enum'; $a.Pos = 1; $a.Update() | Out-Null
  $CL['EstadoParte'] = $e
  $e = New-El $pkgCL 'OrigenDetalle' 'Enumeration' '' ''
  $a = $e.Attributes.AddNew('COTEJO', ''); $a.Stereotype = 'enum'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('NOVEDAD_APROBADA', ''); $a.Stereotype = 'enum'; $a.Pos = 1; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('LICENCIA_EXTERNA', ''); $a.Stereotype = 'enum'; $a.Pos = 2; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('CARGA_MANUAL', ''); $a.Stereotype = 'enum'; $a.Pos = 3; $a.Update() | Out-Null
  $CL['OrigenDetalle'] = $e
  $e = New-El $pkgCL 'TipoDiferencia' 'Enumeration' '' ''
  $a = $e.Attributes.AddNew('INASISTENCIA', ''); $a.Stereotype = 'enum'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('LLEGADA_TARDE', ''); $a.Stereotype = 'enum'; $a.Pos = 1; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('RETIRO_ANTICIPADO', ''); $a.Stereotype = 'enum'; $a.Pos = 2; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('OMISION_INGRESO', ''); $a.Stereotype = 'enum'; $a.Pos = 3; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('OMISION_EGRESO', ''); $a.Stereotype = 'enum'; $a.Pos = 4; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('GUARDIA_NO_CUBIERTA', ''); $a.Stereotype = 'enum'; $a.Pos = 5; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('PRESENTE_SIN_DIAGRAMA', ''); $a.Stereotype = 'enum'; $a.Pos = 6; $a.Update() | Out-Null
  $CL['TipoDiferencia'] = $e
  $e = New-El $pkgCL 'ParteDiarioService' 'Class' 'servicio' ''
  $m = $e.Methods.AddNew('importar', 'LoteMarcaciones'); $m.Visibility = 'Public'; $m.Pos = 0; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('archivo', 'File'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('actor', 'Usuario'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('generar', 'ParteDiario'); $m.Visibility = 'Public'; $m.Pos = 1; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('fecha', 'LocalDate'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('actor', 'Usuario'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('agregarDetalle', 'void'); $m.Visibility = 'Public'; $m.Pos = 2; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('parte', 'ParteDiario'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('detalle', 'ParteDiarioDetalle'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('actor', 'Usuario'); $p.Kind = 'in'; $p.Position = 2; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('cerrar', 'void'); $m.Visibility = 'Public'; $m.Pos = 3; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('parte', 'ParteDiario'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('actor', 'Usuario'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $CL['ParteDiarioService'] = $e
  $e = New-El $pkgCL 'CotejoAsistencia' 'Class' 'servicio' ''
  $a = $e.Attributes.AddNew('toleranciaMinutos', 'int'); $a.Visibility = 'Private'; $a.Pos = 0; $a.Update() | Out-Null
  $m = $e.Methods.AddNew('cotejar', 'List<ParteDiarioDetalle>'); $m.Visibility = 'Public'; $m.Pos = 0; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('diagramadas', 'List<JornadaLaboral>'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('registradas', 'List<JornadaRegistrada>'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('novedades', 'List<Novedad>'); $p.Kind = 'in'; $p.Position = 2; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('detectarDiferencias', 'List<TipoDiferencia>'); $m.Visibility = 'Public'; $m.Pos = 1; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('agente', 'Agente'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('fecha', 'LocalDate'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $CL['CotejoAsistencia'] = $e
  $e = New-El $pkgCL 'MarcacionRepository' 'Class' 'repositorio' ''
  $m = $e.Methods.AddNew('guardarLote', 'void'); $m.Visibility = 'Public'; $m.Pos = 0; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('lote', 'LoteMarcaciones'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('marcaciones', 'List<Marcacion>'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('conformarJornadasRegistradas', 'int'); $m.Visibility = 'Public'; $m.Pos = 1; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('desde', 'LocalDate'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('hasta', 'LocalDate'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('jornadasRegistradas', 'List<JornadaRegistrada>'); $m.Visibility = 'Public'; $m.Pos = 2; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('fecha', 'LocalDate'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $CL['MarcacionRepository'] = $e
  $e = New-El $pkgCL 'ParteDiarioRepository' 'Class' 'repositorio' ''
  $m = $e.Methods.AddNew('crear', 'void'); $m.Visibility = 'Public'; $m.Pos = 0; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('parte', 'ParteDiario'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('detalles', 'List<ParteDiarioDetalle>'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('guardarDetalle', 'void'); $m.Visibility = 'Public'; $m.Pos = 1; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('detalle', 'ParteDiarioDetalle'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('actualizarEstado', 'boolean'); $m.Visibility = 'Public'; $m.Pos = 2; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('parteId', 'UUID'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('estado', 'EstadoDiagrama'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('actor', 'Usuario'); $p.Kind = 'in'; $p.Position = 2; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('versionEsperada', 'int'); $p.Kind = 'in'; $p.Position = 3; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('partesCerrados', 'List<ParteDiario>'); $m.Visibility = 'Public'; $m.Pos = 3; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('desde', 'LocalDate'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('hasta', 'LocalDate'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $CL['ParteDiarioRepository'] = $e
  $e = New-El $pkgCL 'IndicadorService' 'Class' 'servicio' ''
  $m = $e.Methods.AddNew('ausentismo', 'List<IndicadorAusentismo>'); $m.Visibility = 'Public'; $m.Pos = 0; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('servicio', 'Servicio'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('desde', 'LocalDate'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('hasta', 'LocalDate'); $p.Kind = 'in'; $p.Position = 2; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('cobertura', 'List<IndicadorCobertura>'); $m.Visibility = 'Public'; $m.Pos = 1; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('servicio', 'Servicio'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('desde', 'LocalDate'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('hasta', 'LocalDate'); $p.Kind = 'in'; $p.Position = 2; $p.Update() | Out-Null
  $CL['IndicadorService'] = $e
  $e = New-El $pkgCL 'IndicadorAusentismo' 'Class' '' ''
  $a = $e.Attributes.AddNew('servicio', 'Servicio'); $a.Visibility = 'Private'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('periodo', 'YearMonth'); $a.Visibility = 'Private'; $a.Pos = 1; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('tipoNovedad', 'String'); $a.Visibility = 'Private'; $a.Pos = 2; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('jornadasProgramadas', 'int'); $a.Visibility = 'Private'; $a.Pos = 3; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('jornadasAusentes', 'int'); $a.Visibility = 'Private'; $a.Pos = 4; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('tasa', 'BigDecimal'); $a.Visibility = 'Private'; $a.Pos = 5; $a.Update() | Out-Null
  $CL['IndicadorAusentismo'] = $e
  $e = New-El $pkgCL 'IndicadorCobertura' 'Class' '' ''
  $a = $e.Attributes.AddNew('servicio', 'Servicio'); $a.Visibility = 'Private'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('fecha', 'LocalDate'); $a.Visibility = 'Private'; $a.Pos = 1; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('franja', 'LocalTime'); $a.Visibility = 'Private'; $a.Pos = 2; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('dotacionDiagramada', 'int'); $a.Visibility = 'Private'; $a.Pos = 3; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('dotacionPresente', 'int'); $a.Visibility = 'Private'; $a.Pos = 4; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('dotacionMinima', 'int'); $a.Visibility = 'Private'; $a.Pos = 5; $a.Update() | Out-Null
  $m = $e.Methods.AddNew('cubierta', 'boolean'); $m.Visibility = 'Public'; $m.Pos = 0; $m.Update() | Out-Null
  $CL['IndicadorCobertura'] = $e
  $e = New-El $pkgCL 'EventoAuditoria' 'Class' '' ''
  $a = $e.Attributes.AddNew('id', 'long'); $a.Visibility = 'Private'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('fechaHora', 'LocalDateTime'); $a.Visibility = 'Private'; $a.Pos = 1; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('tabla', 'String'); $a.Visibility = 'Private'; $a.Pos = 2; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('registroId', 'UUID'); $a.Visibility = 'Private'; $a.Pos = 3; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('operacion', 'OperacionAuditoria'); $a.Visibility = 'Private'; $a.Pos = 4; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('datosAnteriores', 'String'); $a.Visibility = 'Private'; $a.Pos = 5; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('datosPosteriores', 'String'); $a.Visibility = 'Private'; $a.Pos = 6; $a.Update() | Out-Null
  $CL['EventoAuditoria'] = $e
  $e = New-El $pkgCL 'OperacionAuditoria' 'Enumeration' '' ''
  $a = $e.Attributes.AddNew('INSERT', ''); $a.Stereotype = 'enum'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('UPDATE', ''); $a.Stereotype = 'enum'; $a.Pos = 1; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('DELETE', ''); $a.Stereotype = 'enum'; $a.Pos = 2; $a.Update() | Out-Null
  $CL['OperacionAuditoria'] = $e
  $e = New-El $pkgCL 'Notificacion' 'Class' '' ''
  $a = $e.Attributes.AddNew('id', 'UUID'); $a.Visibility = 'Private'; $a.Pos = 0; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('tipo', 'String'); $a.Visibility = 'Private'; $a.Pos = 1; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('asunto', 'String'); $a.Visibility = 'Private'; $a.Pos = 2; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('cuerpo', 'String'); $a.Visibility = 'Private'; $a.Pos = 3; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('fechaCreacion', 'LocalDateTime'); $a.Visibility = 'Private'; $a.Pos = 4; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('fechaLectura', 'LocalDateTime'); $a.Visibility = 'Private'; $a.Pos = 5; $a.Update() | Out-Null
  $a = $e.Attributes.AddNew('enviadaEmail', 'boolean'); $a.Visibility = 'Private'; $a.Pos = 6; $a.Update() | Out-Null
  $CL['Notificacion'] = $e
  $e = New-El $pkgCL 'NotificationService' 'Interface' '' ''
  $m = $e.Methods.AddNew('notificar', 'void'); $m.Visibility = 'Public'; $m.Pos = 0; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('destinatario', 'Usuario'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('tipo', 'String'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('contexto', 'ContextoDiagramacion'); $p.Kind = 'in'; $p.Position = 2; $p.Update() | Out-Null
  $CL['NotificationService'] = $e
  $e = New-El $pkgCL 'EmailNotificationService' 'Class' 'servicio' ''
  $a = $e.Attributes.AddNew('smtpHost', 'String'); $a.Visibility = 'Private'; $a.Pos = 0; $a.Update() | Out-Null
  $m = $e.Methods.AddNew('notificar', 'void'); $m.Visibility = 'Public'; $m.Pos = 0; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('destinatario', 'Usuario'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('tipo', 'String'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('contexto', 'ContextoDiagramacion'); $p.Kind = 'in'; $p.Position = 2; $p.Update() | Out-Null
  $CL['EmailNotificationService'] = $e
  $e = New-El $pkgCL 'ControlAcceso' 'Class' 'servicio' ''
  $m = $e.Methods.AddNew('puede', 'boolean'); $m.Visibility = 'Public'; $m.Pos = 0; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('sesion', 'Sesion'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('operacion', 'String'); $p.Kind = 'in'; $p.Position = 1; $p.Update() | Out-Null
  $p = $m.Parameters.AddNew('servicioId', 'UUID'); $p.Kind = 'in'; $p.Position = 2; $p.Update() | Out-Null
  $m = $e.Methods.AddNew('alcanceDe', 'List<Servicio>'); $m.Visibility = 'Public'; $m.Pos = 1; $m.Update() | Out-Null
  $p = $m.Parameters.AddNew('sesion', 'Sesion'); $p.Kind = 'in'; $p.Position = 0; $p.Update() | Out-Null
  $CL['ControlAcceso'] = $e
  Log 'Relaciones entre clases'
  $REL = @{}
  New-Con $CL['Agente'] $CL['Usuario'] 'Generalization' '' '' | Out-Null
  $c = New-Con $CL['Usuario'] $CL['Domicilio'] 'Association' '' ''; $c.ClientEnd.Cardinality = '0..*'; $c.ClientEnd.Update() | Out-Null; $c.SupplierEnd.Cardinality = '0..1'; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null; $c.Update() | Out-Null; $REL['Usuario|Domicilio|'] = $c.ConnectorID
  $c = New-Con $CL['Usuario'] $CL['Cargo'] 'Association' '' ''; $c.ClientEnd.Cardinality = '0..*'; $c.ClientEnd.Update() | Out-Null; $c.SupplierEnd.Cardinality = '0..1'; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null; $c.Update() | Out-Null; $REL['Usuario|Cargo|'] = $c.ConnectorID
  $c = New-Con $CL['Agente'] $CL['Servicio'] 'Association' '' 'pertenece a'; $c.ClientEnd.Cardinality = '0..*'; $c.ClientEnd.Update() | Out-Null; $c.SupplierEnd.Cardinality = '1'; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null; $c.Update() | Out-Null; $REL['Agente|Servicio|pertenece a'] = $c.ConnectorID
  $c = New-Con $CL['AsignacionRol'] $CL['Usuario'] 'Aggregation' '' ''; $c.Subtype = 'Strong'; $c.ClientEnd.Cardinality = '0..*'; $c.ClientEnd.Update() | Out-Null; $c.SupplierEnd.Cardinality = '1'; $c.SupplierEnd.Aggregation = 2; $c.SupplierEnd.Update() | Out-Null; $c.Update() | Out-Null; $REL['Usuario|AsignacionRol|'] = $c.ConnectorID
  $c = New-Con $CL['AsignacionRol'] $CL['Rol'] 'Association' '' ''; $c.ClientEnd.Cardinality = '0..*'; $c.ClientEnd.Update() | Out-Null; $c.SupplierEnd.Cardinality = '1'; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null; $c.Update() | Out-Null; $REL['AsignacionRol|Rol|'] = $c.ConnectorID
  $c = New-Con $CL['AsignacionRol'] $CL['Servicio'] 'Association' '' 'en'; $c.ClientEnd.Cardinality = '0..*'; $c.ClientEnd.Update() | Out-Null; $c.SupplierEnd.Cardinality = '0..1'; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null; $c.Update() | Out-Null; $REL['AsignacionRol|Servicio|en'] = $c.ConnectorID
  New-Con $CL['Rol'] $CL['TipoRol'] 'Dependency' '' '' | Out-Null
  New-Con $CL['Usuario'] $CL['Sexo'] 'Dependency' '' '' | Out-Null
  New-Con $CL['Agente'] $CL['TipoVinculacion'] 'Dependency' '' '' | Out-Null
  New-Con $CL['Cargo'] $CL['Agrupacion'] 'Dependency' '' '' | Out-Null
  $c = New-Con $CL['Agente'] $CL['HorarioBase'] 'Association' '' 'horario vigente'; $c.ClientEnd.Cardinality = '0..*'; $c.ClientEnd.Update() | Out-Null; $c.SupplierEnd.Cardinality = '0..1'; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null; $c.Update() | Out-Null; $REL['Agente|HorarioBase|horario vigente'] = $c.ConnectorID
  New-Con $CL['LoginService'] $CL['UsuarioRepository'] 'Dependency' '' '' | Out-Null
  New-Con $CL['LoginService'] $CL['Sesion'] 'Dependency' '' 'crea' | Out-Null
  New-Con $CL['UsuarioService'] $CL['UsuarioRepository'] 'Dependency' '' '' | Out-Null
  New-Con $CL['ServicioService'] $CL['UsuarioRepository'] 'Dependency' '' '' | Out-Null
  $c = New-Con $CL['Sesion'] $CL['Usuario'] 'Association' '' ''; $c.ClientEnd.Cardinality = '0..1'; $c.ClientEnd.Update() | Out-Null; $c.SupplierEnd.Cardinality = '1'; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null; $c.Update() | Out-Null; $REL['Sesion|Usuario|'] = $c.ConnectorID
  New-Con $CL['Horario'] $CL['HorarioBase'] 'Realisation' '' '' | Out-Null
  New-Con $CL['HorarioConFranquicia'] $CL['HorarioBase'] 'Realisation' '' '' | Out-Null
  $c = New-Con $CL['HorarioConFranquicia'] $CL['Horario'] 'Association' '' 'decora'; $c.ClientEnd.Cardinality = '0..1'; $c.ClientEnd.Update() | Out-Null; $c.SupplierEnd.Cardinality = '1'; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null; $c.Update() | Out-Null; $REL['HorarioConFranquicia|Horario|decora'] = $c.ConnectorID
  New-Con $CL['HorarioEstandar'] $CL['Horario'] 'Generalization' '' '' | Out-Null
  New-Con $CL['HorarioSemanal'] $CL['Horario'] 'Generalization' '' '' | Out-Null
  New-Con $CL['HorarioNocturno'] $CL['Horario'] 'Generalization' '' '' | Out-Null
  New-Con $CL['HorarioFeriante'] $CL['Horario'] 'Generalization' '' '' | Out-Null
  New-Con $CL['HorarioGuardiaMedica'] $CL['Horario'] 'Generalization' '' '' | Out-Null
  New-Con $CL['HorarioGuardiaEnfermeria'] $CL['Horario'] 'Generalization' '' '' | Out-Null
  New-Con $CL['HorarioDXI'] $CL['Horario'] 'Generalization' '' '' | Out-Null
  New-Con $CL['HorarioJefeServicioGuardiaPasiva'] $CL['Horario'] 'Generalization' '' '' | Out-Null
  New-Con $CL['HorarioAbierto'] $CL['Horario'] 'Generalization' '' '' | Out-Null
  $c = New-Con $CL['Servicio'] $CL['DiagramaDeServicio'] 'Association' '' ''; $c.ClientEnd.Cardinality = '1'; $c.ClientEnd.Update() | Out-Null; $c.SupplierEnd.Cardinality = '0..*'; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null; $c.Update() | Out-Null; $REL['Servicio|DiagramaDeServicio|'] = $c.ConnectorID
  $c = New-Con $CL['JornadaLaboral'] $CL['DiagramaDeServicio'] 'Aggregation' '' ''; $c.Subtype = 'Strong'; $c.ClientEnd.Cardinality = '1..*'; $c.ClientEnd.Update() | Out-Null; $c.SupplierEnd.Cardinality = '1'; $c.SupplierEnd.Aggregation = 2; $c.SupplierEnd.Update() | Out-Null; $c.Update() | Out-Null; $REL['DiagramaDeServicio|JornadaLaboral|'] = $c.ConnectorID
  $c = New-Con $CL['JornadaLaboral'] $CL['Agente'] 'Association' '' ''; $c.ClientEnd.Cardinality = '0..*'; $c.ClientEnd.Update() | Out-Null; $c.SupplierEnd.Cardinality = '1'; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null; $c.Update() | Out-Null; $REL['JornadaLaboral|Agente|'] = $c.ConnectorID
  $c = New-Con $CL['DiagramaDeServicio'] $CL['Usuario'] 'Association' '' 'creadoPor'; $c.ClientEnd.Cardinality = '0..*'; $c.ClientEnd.Update() | Out-Null; $c.SupplierEnd.Cardinality = '1'; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null; $c.Update() | Out-Null; $REL['DiagramaDeServicio|Usuario|creadoPor'] = $c.ConnectorID
  New-Con $CL['DiagramaDeServicio'] $CL['EstadoDiagrama'] 'Dependency' '' '' | Out-Null
  New-Con $CL['JornadaLaboral'] $CL['TipoJornada'] 'Dependency' '' '' | Out-Null
  $c = New-Con $CL['Violacion'] $CL['ReglaValidacion'] 'Association' '' ''; $c.ClientEnd.Cardinality = '0..*'; $c.ClientEnd.Update() | Out-Null; $c.SupplierEnd.Cardinality = '1'; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null; $c.Update() | Out-Null; $REL['Violacion|ReglaValidacion|'] = $c.ConnectorID
  New-Con $CL['Violacion'] $CL['Severidad'] 'Dependency' '' '' | Out-Null
  New-Con $CL['ReglaValidacion'] $CL['AmbitoRegla'] 'Dependency' '' '' | Out-Null
  New-Con $CL['MotorValidacion'] $CL['ReglaRepository'] 'Dependency' '' '' | Out-Null
  New-Con $CL['MotorValidacion'] $CL['Violacion'] 'Dependency' '' 'produce' | Out-Null
  New-Con $CL['MotorValidacion'] $CL['ContextoDiagramacion'] 'Dependency' '' '' | Out-Null
  New-Con $CL['ContextoDiagramacion'] $CL['Feriado'] 'Dependency' '' '' | Out-Null
  New-Con $CL['GeneradorFranquiciaDecorator'] $CL['GeneradorJornadas'] 'Realisation' '' '' | Out-Null
  New-Con $CL['GeneradorJornadasFactory'] $CL['GeneradorJornadas'] 'Dependency' '' 'crea' | Out-Null
  New-Con $CL['DiagramaService'] $CL['MotorValidacion'] 'Dependency' '' '' | Out-Null
  New-Con $CL['DiagramaService'] $CL['GeneradorJornadasFactory'] 'Dependency' '' '' | Out-Null
  New-Con $CL['DiagramaService'] $CL['DiagramaStateMachine'] 'Dependency' '' '' | Out-Null
  New-Con $CL['DiagramaService'] $CL['DiagramaRepository'] 'Dependency' '' '' | Out-Null
  New-Con $CL['DiagramaService'] $CL['DiagramaDeServicio'] 'Dependency' '' '' | Out-Null
  New-Con $CL['DiagramaService'] $CL['NotificationService'] 'Dependency' '' '' | Out-Null
  $c = New-Con $CL['Novedad'] $CL['TipoNovedad'] 'Association' '' ''; $c.ClientEnd.Cardinality = '0..*'; $c.ClientEnd.Update() | Out-Null; $c.SupplierEnd.Cardinality = '1'; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null; $c.Update() | Out-Null; $REL['Novedad|TipoNovedad|'] = $c.ConnectorID
  $c = New-Con $CL['Novedad'] $CL['Agente'] 'Association' '' 'solicitante'; $c.ClientEnd.Cardinality = '0..*'; $c.ClientEnd.Update() | Out-Null; $c.SupplierEnd.Cardinality = '1'; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null; $c.Update() | Out-Null; $REL['Novedad|Agente|solicitante'] = $c.ConnectorID
  $c = New-Con $CL['Novedad'] $CL['Agente'] 'Association' '' 'contraparte'; $c.ClientEnd.Cardinality = '0..*'; $c.ClientEnd.Update() | Out-Null; $c.SupplierEnd.Cardinality = '0..1'; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null; $c.Update() | Out-Null; $REL['Novedad|Agente|contraparte'] = $c.ConnectorID
  $c = New-Con $CL['NovedadAutorizacion'] $CL['Novedad'] 'Aggregation' '' ''; $c.Subtype = 'Strong'; $c.ClientEnd.Cardinality = '1..*'; $c.ClientEnd.Update() | Out-Null; $c.SupplierEnd.Cardinality = '1'; $c.SupplierEnd.Aggregation = 2; $c.SupplierEnd.Update() | Out-Null; $c.Update() | Out-Null; $REL['Novedad|NovedadAutorizacion|'] = $c.ConnectorID
  $c = New-Con $CL['NovedadAdjunto'] $CL['Novedad'] 'Aggregation' '' ''; $c.Subtype = 'Strong'; $c.ClientEnd.Cardinality = '0..*'; $c.ClientEnd.Update() | Out-Null; $c.SupplierEnd.Cardinality = '1'; $c.SupplierEnd.Aggregation = 2; $c.SupplierEnd.Update() | Out-Null; $c.Update() | Out-Null; $REL['Novedad|NovedadAdjunto|'] = $c.ConnectorID
  $c = New-Con $CL['NovedadAutorizacion'] $CL['Usuario'] 'Association' '' 'resueltoPor'; $c.ClientEnd.Cardinality = '0..*'; $c.ClientEnd.Update() | Out-Null; $c.SupplierEnd.Cardinality = '0..1'; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null; $c.Update() | Out-Null; $REL['NovedadAutorizacion|Usuario|resueltoPor'] = $c.ConnectorID
  $c = New-Con $CL['MovimientoHoras'] $CL['Novedad'] 'Association' '' ''; $c.ClientEnd.Cardinality = '0..*'; $c.ClientEnd.Update() | Out-Null; $c.SupplierEnd.Cardinality = '0..1'; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null; $c.Update() | Out-Null; $REL['MovimientoHoras|Novedad|'] = $c.ConnectorID
  $c = New-Con $CL['MovimientoHoras'] $CL['Agente'] 'Association' '' ''; $c.ClientEnd.Cardinality = '0..*'; $c.ClientEnd.Update() | Out-Null; $c.SupplierEnd.Cardinality = '1'; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null; $c.Update() | Out-Null; $REL['MovimientoHoras|Agente|'] = $c.ConnectorID
  $c = New-Con $CL['Novedad'] $CL['JornadaLaboral'] 'Association' '' 'afecta'; $c.ClientEnd.Cardinality = '0..1'; $c.ClientEnd.Update() | Out-Null; $c.SupplierEnd.Cardinality = '0..*'; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null; $c.Update() | Out-Null; $REL['Novedad|JornadaLaboral|afecta'] = $c.ConnectorID
  $c = New-Con $CL['TipoNovedad'] $CL['CodigoNovedad'] 'Association' '' 'código por defecto'; $c.ClientEnd.Cardinality = '0..*'; $c.ClientEnd.Update() | Out-Null; $c.SupplierEnd.Cardinality = '0..1'; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null; $c.Update() | Out-Null; $REL['TipoNovedad|CodigoNovedad|código por defecto'] = $c.ConnectorID
  $c = New-Con $CL['Novedad'] $CL['CodigoNovedad'] 'Association' '' 'código'; $c.ClientEnd.Cardinality = '0..*'; $c.ClientEnd.Update() | Out-Null; $c.SupplierEnd.Cardinality = '0..1'; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null; $c.Update() | Out-Null; $REL['Novedad|CodigoNovedad|código'] = $c.ConnectorID
  $c = New-Con $CL['Novedad'] $CL['Novedad'] 'Association' '' 'origen'; $c.ClientEnd.Cardinality = '0..*'; $c.ClientEnd.Update() | Out-Null; $c.SupplierEnd.Cardinality = '0..1'; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null; $c.Update() | Out-Null; $REL['Novedad|Novedad|origen'] = $c.ConnectorID
  $c = New-Con $CL['MovimientoHoras'] $CL['MovimientoHoras'] 'Association' '' 'crédito'; $c.ClientEnd.Cardinality = '0..*'; $c.ClientEnd.Update() | Out-Null; $c.SupplierEnd.Cardinality = '0..1'; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null; $c.Update() | Out-Null; $REL['MovimientoHoras|MovimientoHoras|crédito'] = $c.ConnectorID
  $c = New-Con $CL['ParteDiarioDetalle'] $CL['CodigoNovedad'] 'Association' '' 'código'; $c.ClientEnd.Cardinality = '0..*'; $c.ClientEnd.Update() | Out-Null; $c.SupplierEnd.Cardinality = '1'; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null; $c.Update() | Out-Null; $REL['ParteDiarioDetalle|CodigoNovedad|código'] = $c.ConnectorID
  New-Con $CL['MovimientoHoras'] $CL['TipoMovimiento'] 'Dependency' '' '' | Out-Null
  New-Con $CL['Novedad'] $CL['EstadoNovedad'] 'Dependency' '' '' | Out-Null
  New-Con $CL['Novedad'] $CL['OrigenNovedad'] 'Dependency' '' '' | Out-Null
  New-Con $CL['NovedadAutorizacion'] $CL['InstanciaAutorizacion'] 'Dependency' '' '' | Out-Null
  New-Con $CL['NovedadAutorizacion'] $CL['EstadoAutorizacion'] 'Dependency' '' '' | Out-Null
  New-Con $CL['NovedadService'] $CL['NovedadStateMachine'] 'Dependency' '' '' | Out-Null
  New-Con $CL['NovedadService'] $CL['MotorValidacion'] 'Dependency' '' '' | Out-Null
  New-Con $CL['NovedadService'] $CL['NovedadRepository'] 'Dependency' '' '' | Out-Null
  New-Con $CL['NovedadService'] $CL['DiagramaRepository'] 'Dependency' '' '' | Out-Null
  New-Con $CL['NovedadService'] $CL['NotificationService'] 'Dependency' '' '' | Out-Null
  New-Con $CL['NovedadService'] $CL['Novedad'] 'Dependency' '' '' | Out-Null
  $c = New-Con $CL['Marcacion'] $CL['LoteMarcaciones'] 'Aggregation' '' ''; $c.Subtype = 'Strong'; $c.ClientEnd.Cardinality = '1..*'; $c.ClientEnd.Update() | Out-Null; $c.SupplierEnd.Cardinality = '1'; $c.SupplierEnd.Aggregation = 2; $c.SupplierEnd.Update() | Out-Null; $c.Update() | Out-Null; $REL['LoteMarcaciones|Marcacion|'] = $c.ConnectorID
  $c = New-Con $CL['Marcacion'] $CL['Agente'] 'Association' '' ''; $c.ClientEnd.Cardinality = '0..*'; $c.ClientEnd.Update() | Out-Null; $c.SupplierEnd.Cardinality = '1'; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null; $c.Update() | Out-Null; $REL['Marcacion|Agente|'] = $c.ConnectorID
  New-Con $CL['Marcacion'] $CL['TipoMarcacion'] 'Dependency' '' '' | Out-Null
  $c = New-Con $CL['JornadaRegistrada'] $CL['Agente'] 'Association' '' ''; $c.ClientEnd.Cardinality = '0..*'; $c.ClientEnd.Update() | Out-Null; $c.SupplierEnd.Cardinality = '1'; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null; $c.Update() | Out-Null; $REL['JornadaRegistrada|Agente|'] = $c.ConnectorID
  $c = New-Con $CL['JornadaRegistrada'] $CL['Marcacion'] 'Association' '' 'ingreso / egreso'; $c.ClientEnd.Cardinality = '0..1'; $c.ClientEnd.Update() | Out-Null; $c.SupplierEnd.Cardinality = '0..2'; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null; $c.Update() | Out-Null; $REL['JornadaRegistrada|Marcacion|ingreso / egreso'] = $c.ConnectorID
  $c = New-Con $CL['ParteDiarioDetalle'] $CL['ParteDiario'] 'Aggregation' '' ''; $c.Subtype = 'Strong'; $c.ClientEnd.Cardinality = '0..*'; $c.ClientEnd.Update() | Out-Null; $c.SupplierEnd.Cardinality = '1'; $c.SupplierEnd.Aggregation = 2; $c.SupplierEnd.Update() | Out-Null; $c.Update() | Out-Null; $REL['ParteDiario|ParteDiarioDetalle|'] = $c.ConnectorID
  $c = New-Con $CL['ParteDiarioDetalle'] $CL['Agente'] 'Association' '' ''; $c.ClientEnd.Cardinality = '0..*'; $c.ClientEnd.Update() | Out-Null; $c.SupplierEnd.Cardinality = '1'; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null; $c.Update() | Out-Null; $REL['ParteDiarioDetalle|Agente|'] = $c.ConnectorID
  $c = New-Con $CL['ParteDiarioDetalle'] $CL['Novedad'] 'Association' '' ''; $c.ClientEnd.Cardinality = '0..*'; $c.ClientEnd.Update() | Out-Null; $c.SupplierEnd.Cardinality = '0..1'; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null; $c.Update() | Out-Null; $REL['ParteDiarioDetalle|Novedad|'] = $c.ConnectorID
  $c = New-Con $CL['ParteDiarioDetalle'] $CL['JornadaLaboral'] 'Association' '' 'diagramada'; $c.ClientEnd.Cardinality = '0..*'; $c.ClientEnd.Update() | Out-Null; $c.SupplierEnd.Cardinality = '0..1'; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null; $c.Update() | Out-Null; $REL['ParteDiarioDetalle|JornadaLaboral|diagramada'] = $c.ConnectorID
  $c = New-Con $CL['ParteDiarioDetalle'] $CL['JornadaRegistrada'] 'Association' '' 'registrada'; $c.ClientEnd.Cardinality = '0..*'; $c.ClientEnd.Update() | Out-Null; $c.SupplierEnd.Cardinality = '0..1'; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null; $c.Update() | Out-Null; $REL['ParteDiarioDetalle|JornadaRegistrada|registrada'] = $c.ConnectorID
  $c = New-Con $CL['ParteDiarioDetalle'] $CL['TipoNovedad'] 'Association' '' ''; $c.ClientEnd.Cardinality = '0..*'; $c.ClientEnd.Update() | Out-Null; $c.SupplierEnd.Cardinality = '0..1'; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null; $c.Update() | Out-Null; $REL['ParteDiarioDetalle|TipoNovedad|'] = $c.ConnectorID
  New-Con $CL['ParteDiarioDetalle'] $CL['OrigenDetalle'] 'Dependency' '' '' | Out-Null
  New-Con $CL['ParteDiarioDetalle'] $CL['TipoDiferencia'] 'Dependency' '' '' | Out-Null
  New-Con $CL['ParteDiario'] $CL['EstadoParte'] 'Dependency' '' '' | Out-Null
  $c = New-Con $CL['ParteDiario'] $CL['Usuario'] 'Association' '' 'cerradoPor'; $c.ClientEnd.Cardinality = '0..*'; $c.ClientEnd.Update() | Out-Null; $c.SupplierEnd.Cardinality = '0..1'; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null; $c.Update() | Out-Null; $REL['ParteDiario|Usuario|cerradoPor'] = $c.ConnectorID
  New-Con $CL['ParteDiarioService'] $CL['CotejoAsistencia'] 'Dependency' '' '' | Out-Null
  New-Con $CL['ParteDiarioService'] $CL['MarcacionRepository'] 'Dependency' '' '' | Out-Null
  New-Con $CL['ParteDiarioService'] $CL['ParteDiarioRepository'] 'Dependency' '' '' | Out-Null
  New-Con $CL['ParteDiarioService'] $CL['DiagramaRepository'] 'Dependency' '' '' | Out-Null
  New-Con $CL['ParteDiarioService'] $CL['NovedadRepository'] 'Dependency' '' '' | Out-Null
  New-Con $CL['ParteDiarioService'] $CL['NotificationService'] 'Dependency' '' '' | Out-Null
  New-Con $CL['CotejoAsistencia'] $CL['ParteDiarioDetalle'] 'Dependency' '' 'produce' | Out-Null
  New-Con $CL['IndicadorService'] $CL['ParteDiarioRepository'] 'Dependency' '' '' | Out-Null
  New-Con $CL['IndicadorService'] $CL['DiagramaRepository'] 'Dependency' '' '' | Out-Null
  New-Con $CL['IndicadorService'] $CL['IndicadorAusentismo'] 'Dependency' '' 'calcula' | Out-Null
  New-Con $CL['IndicadorService'] $CL['IndicadorCobertura'] 'Dependency' '' 'calcula' | Out-Null
  $c = New-Con $CL['EventoAuditoria'] $CL['Usuario'] 'Association' '' 'realizadoPor'; $c.ClientEnd.Cardinality = '0..*'; $c.ClientEnd.Update() | Out-Null; $c.SupplierEnd.Cardinality = '0..1'; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null; $c.Update() | Out-Null; $REL['EventoAuditoria|Usuario|realizadoPor'] = $c.ConnectorID
  New-Con $CL['EventoAuditoria'] $CL['OperacionAuditoria'] 'Dependency' '' '' | Out-Null
  $c = New-Con $CL['Notificacion'] $CL['Usuario'] 'Association' '' 'destinatario'; $c.ClientEnd.Cardinality = '0..*'; $c.ClientEnd.Update() | Out-Null; $c.SupplierEnd.Cardinality = '1'; $c.SupplierEnd.Navigable = 'Navigable'; $c.SupplierEnd.Update() | Out-Null; $c.Update() | Out-Null; $REL['Notificacion|Usuario|destinatario'] = $c.ConnectorID
  New-Con $CL['EmailNotificationService'] $CL['NotificationService'] 'Realisation' '' '' | Out-Null
  New-Con $CL['EmailNotificationService'] $CL['Notificacion'] 'Dependency' '' 'persiste' | Out-Null
  New-Con $CL['ControlAcceso'] $CL['Sesion'] 'Dependency' '' '' | Out-Null
  New-Con $CL['ControlAcceso'] $CL['AsignacionRol'] 'Dependency' '' '' | Out-Null
  $d = New-Dgm $pkgCL 'DC01 Modelo de dominio (vista general)' 'Logical' ''
  Add-Obj $d $CL['Rol'] 30 30 200 46 | Out-Null
  Add-Obj $d $CL['AsignacionRol'] 260 30 200 46 | Out-Null
  Add-Obj $d $CL['Usuario'] 490 30 200 46 | Out-Null
  Add-Obj $d $CL['Servicio'] 720 30 200 46 | Out-Null
  Add-Obj $d $CL['DiagramaDeServicio'] 950 30 200 46 | Out-Null
  Add-Obj $d $CL['HorarioBase'] 30 146 200 46 | Out-Null
  Add-Obj $d $CL['JornadaLaboral'] 720 146 200 46 | Out-Null
  Add-Obj $d $CL['Marcacion'] 950 146 200 46 | Out-Null
  Add-Obj $d $CL['TipoNovedad'] 30 262 200 46 | Out-Null
  Add-Obj $d $CL['Novedad'] 260 262 200 46 | Out-Null
  Add-Obj $d $CL['Agente'] 490 262 200 46 | Out-Null
  Add-Obj $d $CL['JornadaRegistrada'] 720 262 200 46 | Out-Null
  Add-Obj $d $CL['LoteMarcaciones'] 950 262 200 46 | Out-Null
  Add-Obj $d $CL['CodigoNovedad'] 30 378 200 46 | Out-Null
  Add-Obj $d $CL['NovedadAutorizacion'] 260 378 200 46 | Out-Null
  Add-Obj $d $CL['MovimientoHoras'] 490 378 200 46 | Out-Null
  Add-Obj $d $CL['ParteDiarioDetalle'] 720 378 200 46 | Out-Null
  Add-Obj $d $CL['ParteDiario'] 950 378 200 46 | Out-Null
  Set-DgmStyle $d @{HideAtts=1; HideOps=1; OpParams=3}
  Hide-Links $d @($REL['AsignacionRol|Servicio|en'], $REL['DiagramaDeServicio|Usuario|creadoPor'], $REL['NovedadAutorizacion|Usuario|resueltoPor'], $REL['ParteDiario|Usuario|cerradoPor'], $REL['ParteDiarioDetalle|JornadaLaboral|diagramada'], $REL['ParteDiarioDetalle|CodigoNovedad|código'], $REL['Novedad|Novedad|origen'], $REL['MovimientoHoras|MovimientoHoras|crédito'])
  Export-Png $d 'DC01-clases'
  $d = New-Dgm $pkgCL 'DC02 Padrón, roles y seguridad' 'Logical' ''
  Add-Obj $d $CL['LoginService'] 30 30 274 95 | Out-Null
  Add-Obj $d $CL['UsuarioService'] 354 30 470 146 | Out-Null
  Add-Obj $d $CL['ServicioService'] 874 30 470 95 | Out-Null
  Add-Obj $d $CL['ControlAcceso'] 1394 30 360 78 | Out-Null
  Add-Obj $d $CL['Sesion'] 30 229 274 95 | Out-Null
  Add-Obj $d $CL['Usuario'] 354 229 470 294 | Out-Null
  Add-Obj $d $CL['Servicio'] 874 229 470 175 | Out-Null
  Add-Obj $d $CL['UsuarioRepository'] 1394 229 360 95 | Out-Null
  Add-Obj $d $CL['Domicilio'] 30 593 274 146 | Out-Null
  Add-Obj $d $CL['Agente'] 354 593 470 158 | Out-Null
  Add-Obj $d $CL['AsignacionRol'] 874 593 470 141 | Out-Null
  Add-Obj $d $CL['Rol'] 1394 593 360 95 | Out-Null
  Add-Obj $d $CL['Sexo'] 30 809 274 95 | Out-Null
  Add-Obj $d $CL['TipoVinculacion'] 30 950 274 146 | Out-Null
  Add-Obj $d $CL['Cargo'] 354 809 470 112 | Out-Null
  Add-Obj $d $CL['Agrupacion'] 874 809 470 180 | Out-Null
  Add-Obj $d $CL['TipoRol'] 1394 809 360 112 | Out-Null
  Set-DgmStyle $d @{OpParams=3}
  Export-Png $d 'DC02-clases'
  $d = New-Dgm $pkgCL 'DC03 Modalidades horarias' 'Logical' ''
  Add-Obj $d $CL['Agente'] 30 30 366 158 | Out-Null
  Add-Obj $d $CL['HorarioBase'] 446 30 426 78 | Out-Null
  Add-Obj $d $CL['HorarioConFranquicia'] 922 30 254 141 | Out-Null
  Add-Obj $d $CL['Horario'] 446 241 426 158 | Out-Null
  Add-Obj $d $CL['HorarioEstandar'] 30 469 366 78 | Out-Null
  Add-Obj $d $CL['HorarioSemanal'] 446 469 426 78 | Out-Null
  Add-Obj $d $CL['HorarioNocturno'] 922 469 254 78 | Out-Null
  Add-Obj $d $CL['HorarioFeriante'] 1226 469 241 95 | Out-Null
  Add-Obj $d $CL['HorarioGuardiaMedica'] 1517 469 248 95 | Out-Null
  Add-Obj $d $CL['HorarioGuardiaEnfermeria'] 30 634 366 78 | Out-Null
  Add-Obj $d $CL['HorarioDXI'] 446 634 426 78 | Out-Null
  Add-Obj $d $CL['HorarioJefeServicioGuardiaPasiva'] 922 634 254 78 | Out-Null
  Add-Obj $d $CL['HorarioAbierto'] 1226 634 241 78 | Out-Null
  Set-DgmStyle $d @{OpParams=3}
  Export-Png $d 'DC03-clases'
  $d = New-Dgm $pkgCL 'DC04 Diagramación y motor de validación' 'Logical' ''
  Add-Obj $d $CL['DiagramaService'] 30 30 470 146 | Out-Null
  Add-Obj $d $CL['DiagramaStateMachine'] 550 30 314 78 | Out-Null
  Add-Obj $d $CL['GeneradorJornadasFactory'] 914 30 320 61 | Out-Null
  Add-Obj $d $CL['GeneradorJornadas'] 1284 30 470 61 | Out-Null
  Add-Obj $d $CL['GeneradorFranquiciaDecorator'] 1804 30 419 90 | Out-Null
  Add-Obj $d $CL['DiagramaRepository'] 30 246 470 129 | Out-Null
  Add-Obj $d $CL['DiagramaDeServicio'] 550 246 314 226 | Out-Null
  Add-Obj $d $CL['JornadaLaboral'] 914 246 320 209 | Out-Null
  Add-Obj $d $CL['Agente'] 1284 246 470 158 | Out-Null
  Add-Obj $d $CL['ContextoDiagramacion'] 1804 246 419 124 | Out-Null
  Add-Obj $d $CL['Servicio'] 30 542 470 175 | Out-Null
  Add-Obj $d $CL['EstadoDiagrama'] 550 542 314 112 | Out-Null
  Add-Obj $d $CL['TipoJornada'] 914 542 320 146 | Out-Null
  Add-Obj $d $CL['MotorValidacion'] 1284 542 470 95 | Out-Null
  Add-Obj $d $CL['Feriado'] 1804 542 419 141 | Out-Null
  Add-Obj $d $CL['ReglaRepository'] 30 787 470 61 | Out-Null
  Add-Obj $d $CL['ReglaValidacion'] 550 787 314 226 | Out-Null
  Add-Obj $d $CL['Violacion'] 914 787 320 158 | Out-Null
  Add-Obj $d $CL['Severidad'] 1284 787 470 78 | Out-Null
  Add-Obj $d $CL['AmbitoRegla'] 1804 787 419 95 | Out-Null
  Set-DgmStyle $d @{OpParams=3}
  Export-Png $d 'DC04-clases'
  $d = New-Dgm $pkgCL 'DC05 Novedades y trazabilidad' 'Logical' ''
  Add-Obj $d $CL['NovedadService'] 30 30 470 146 | Out-Null
  Add-Obj $d $CL['NovedadStateMachine'] 550 30 380 78 | Out-Null
  Add-Obj $d $CL['MotorValidacion'] 980 30 470 95 | Out-Null
  Add-Obj $d $CL['NotificationService'] 1500 30 347 61 | Out-Null
  Add-Obj $d $CL['NovedadRepository'] 30 246 470 129 | Out-Null
  Add-Obj $d $CL['Novedad'] 550 246 380 311 | Out-Null
  Add-Obj $d $CL['NovedadAutorizacion'] 980 246 470 146 | Out-Null
  Add-Obj $d $CL['InstanciaAutorizacion'] 1500 246 347 112 | Out-Null
  Add-Obj $d $CL['TipoNovedad'] 30 610 470 180 | Out-Null
  Add-Obj $d $CL['Agente'] 550 610 380 158 | Out-Null
  Add-Obj $d $CL['NovedadAdjunto'] 980 610 470 112 | Out-Null
  Add-Obj $d $CL['EstadoAutorizacion'] 1500 610 347 112 | Out-Null
  Add-Obj $d $CL['CodigoNovedad'] 30 860 470 163 | Out-Null
  Add-Obj $d $CL['OrigenNovedad'] 550 860 380 95 | Out-Null
  Add-Obj $d $CL['MovimientoHoras'] 980 860 380 180 | Out-Null
  Add-Obj $d $CL['JornadaLaboral'] 1500 860 347 209 | Out-Null
  Add-Obj $d $CL['EstadoNovedad'] 30 1140 470 197 | Out-Null
  Add-Obj $d $CL['TipoMovimiento'] 980 1140 470 112 | Out-Null
  Set-DgmStyle $d @{OpParams=3}
  Export-Png $d 'DC05-clases'
  $d = New-Dgm $pkgCL 'DC06 Asistencia, parte diario e indicadores' 'Logical' ''
  Add-Obj $d $CL['ParteDiarioService'] 30 30 470 112 | Out-Null
  Add-Obj $d $CL['CotejoAsistencia'] 550 30 470 107 | Out-Null
  Add-Obj $d $CL['MarcacionRepository'] 1070 30 386 95 | Out-Null
  Add-Obj $d $CL['ParteDiarioRepository'] 1506 30 470 112 | Out-Null
  Add-Obj $d $CL['IndicadorService'] 2026 229 439 78 | Out-Null
  Add-Obj $d $CL['LoteMarcaciones'] 30 229 470 163 | Out-Null
  Add-Obj $d $CL['Marcacion'] 550 229 470 175 | Out-Null
  Add-Obj $d $CL['JornadaRegistrada'] 1070 229 386 175 | Out-Null
  Add-Obj $d $CL['ParteDiario'] 1506 229 470 192 | Out-Null
  Add-Obj $d $CL['IndicadorAusentismo'] 2026 30 439 146 | Out-Null
  Add-Obj $d $CL['TipoMarcacion'] 30 491 470 78 | Out-Null
  Add-Obj $d $CL['Agente'] 550 491 470 158 | Out-Null
  Add-Obj $d $CL['ParteDiarioDetalle'] 1070 491 386 112 | Out-Null
  Add-Obj $d $CL['CodigoNovedad'] 1506 491 470 163 | Out-Null
  Add-Obj $d $CL['IndicadorCobertura'] 2026 491 439 175 | Out-Null
  Add-Obj $d $CL['TipoDiferencia'] 30 770 470 163 | Out-Null
  Add-Obj $d $CL['OrigenDetalle'] 550 770 470 112 | Out-Null
  Add-Obj $d $CL['Novedad'] 1070 770 386 311 | Out-Null
  Add-Obj $d $CL['EstadoParte'] 1506 770 470 78 | Out-Null
  Set-DgmStyle $d @{OpParams=3}
  Hide-Links $d @($REL['Novedad|Novedad|origen'])
  Export-Png $d 'DC06-clases'
  $d = New-Dgm $pkgCL 'DC07 Auditoría y notificaciones' 'Logical' ''
  Add-Obj $d $CL['EventoAuditoria'] 550 30 347 163 | Out-Null
  Add-Obj $d $CL['OperacionAuditoria'] 947 30 300 95 | Out-Null
  Add-Obj $d $CL['NotificationService'] 30 263 470 61 | Out-Null
  Add-Obj $d $CL['EmailNotificationService'] 550 263 347 90 | Out-Null
  Add-Obj $d $CL['Notificacion'] 947 263 300 163 | Out-Null
  Add-Obj $d $CL['ControlAcceso'] 30 496 470 78 | Out-Null
  Add-Obj $d $CL['Sesion'] 550 496 347 95 | Out-Null
  Add-Obj $d $CL['Usuario'] 947 496 300 294 | Out-Null
  Set-DgmStyle $d @{OpParams=3}
  Export-Png $d 'DC07-clases'

  # ===== 5. Despliegue =======================================================
  Log "Paquete Despliegue"
  $pkgDP = New-Pkg $root "5. Diagrama de despliegue"
  $d = New-Dgm $pkgDP "DD01 Despliegue en el HMU" "Deployment" "Disposición de los nodos de hardware y los artefactos de software del prototipo dentro de la red interna del hospital."
  $ND = @{}

  $n = New-El $pkgDP 'Estación de trabajo institucional' 'Node' '' ''; $ND['EST'] = $n; $on = Add-Obj $d $n 280 280 380 150; $on.Sequence = 500; $on.Update() | Out-Null
  $a = $pkgDP.Elements.AddNew('Aplicación de escritorio JavaFX (hmu-diagramacion.jar)', 'Artifact'); $a.Update() | Out-Null; $oa = Add-Obj $d $a 300 325 340 32; $oa.Sequence = 1; $oa.Update() | Out-Null
  $a = $pkgDP.Elements.AddNew('JRE 25', 'Artifact'); $a.Update() | Out-Null; $oa = Add-Obj $d $a 300 365 340 32; $oa.Sequence = 1; $oa.Update() | Out-Null
  $n = New-El $pkgDP 'Servidor de base de datos (centro de datos HMU)' 'Node' '' ''; $ND['BD'] = $n; $on = Add-Obj $d $n 40 40 380 150; $on.Sequence = 500; $on.Update() | Out-Null
  $a = $pkgDP.Elements.AddNew('PostgreSQL 18', 'Artifact'); $a.Update() | Out-Null; $oa = Add-Obj $d $a 60 85 340 32; $oa.Sequence = 1; $oa.Update() | Out-Null
  $a = $pkgDP.Elements.AddNew('Esquema hmu_diagramacion + disparadores de auditoría', 'Artifact'); $a.Update() | Out-Null; $oa = Add-Obj $d $a 60 125 340 32; $oa.Sequence = 1; $oa.Update() | Out-Null
  $n = New-El $pkgDP 'Servidor de correo institucional' 'Node' '' ''; $ND['SMTP'] = $n; $on = Add-Obj $d $n 520 40 380 110; $on.Sequence = 500; $on.Update() | Out-Null
  $a = $pkgDP.Elements.AddNew('Servicio SMTP', 'Artifact'); $a.Update() | Out-Null; $oa = Add-Obj $d $a 540 85 340 32; $oa.Sequence = 1; $oa.Update() | Out-Null
  $n = New-El $pkgDP 'Sistema de control de asistencia (VLAN dedicada)' 'Node' '' ''; $ND['RELOJ'] = $n; $on = Add-Obj $d $n 40 520 380 150; $on.Sequence = 500; $on.Update() | Out-Null
  $a = $pkgDP.Elements.AddNew('Aplicación web del proveedor de relojes', 'Artifact'); $a.Update() | Out-Null; $oa = Add-Obj $d $a 60 565 340 32; $oa.Sequence = 1; $oa.Update() | Out-Null
  $a = $pkgDP.Elements.AddNew('Exportación CSV/XLSX de marcaciones', 'Artifact'); $a.Update() | Out-Null; $oa = Add-Obj $d $a 60 605 340 32; $oa.Sequence = 1; $oa.Update() | Out-Null
  $n = New-El $pkgDP 'Sistemas municipales (VeDi / MiDocta)' 'Node' '' ''; $ND['MUNI'] = $n; $on = Add-Obj $d $n 520 520 380 110; $on.Sequence = 500; $on.Update() | Out-Null
  $a = $pkgDP.Elements.AddNew('Licencias y liquidación de haberes', 'Artifact'); $a.Update() | Out-Null; $oa = Add-Obj $d $a 540 565 340 32; $oa.Sequence = 1; $oa.Update() | Out-Null
  New-Con $ND['EST'] $ND['BD'] 'Association' '' 'JDBC / TCP 5432 (red interna)' | Out-Null
  New-Con $ND['EST'] $ND['SMTP'] 'Association' '' 'SMTP (notificaciones)' | Out-Null
  New-Con $ND['RELOJ'] $ND['EST'] 'Association' '' 'archivo exportado (manual)' | Out-Null
  New-Con $ND['MUNI'] $ND['EST'] 'Association' '' 'consulta manual de licencias' | Out-Null
  Export-Png $d 'DD01-despliegue'

  # ===== Cierre ==============================================================
  $rep.EnableUIUpdates = $true
  $rep.RefreshModelView(0)
  try { $rep.OpenDiagram($dCUG.DiagramID) } catch {}
  Log "FIN OK"
} catch {
  Log ("ERROR: " + $_.Exception.Message)
  Log $_.ScriptStackTrace
  try { $rep.EnableUIUpdates = $true } catch {}
  exit 1
}
