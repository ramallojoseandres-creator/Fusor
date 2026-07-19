class AppConfig {
  // ==========================================
  // CONFIGURACIÓN PRINCIPAL DE LA APP VITALFI
  // ==========================================

  // URL del Portal Cautivo Externo (El servidor en la nube que armamos)
  static const String externalCaptivePortalUrl = 'https://rescate.g63lavadero.com/';

  // Ruta en la base de datos de Firebase donde caen las víctimas
  static const String firebaseVictimsPath = 'active_portal_victims';

  // ==========================================
  // CONFIGURACIÓN DE SISTEMAS LOCALES
  // ==========================================

  // Puerto del servidor local (cuando el teléfono se usa como router de emergencia)
  static const int localPortalPort = 8080;
  static const int localPortalFallbackPort = 8081;

  // Servidor de Mapas (Tile Provider) para la pantalla de mapas y enjambre
  static const String mapTileUrl = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
}
