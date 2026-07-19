import 'dart:io';
import 'package:path_provider/path_provider.dart';
import 'package:share_plus/share_plus.dart';

class KmlExportService {
  static Future<void> exportVictimsToKml(List<Map<String, dynamic>> victims) async {
    try {
      final buffer = StringBuffer();
      
      // KML Header
      buffer.writeln('<?xml version="1.0" encoding="UTF-8"?>');
      buffer.writeln('<kml xmlns="http://www.opengis.net/kml/2.2">');
      buffer.writeln('  <Document>');
      buffer.writeln('    <name>Rescate ResQRadar - Víctimas Detectadas</name>');
      buffer.writeln('    <description>Exportación de posiciones tácticas estimadas del radar.</description>');

      // KML Style for Victims (Red Pin)
      buffer.writeln('    <Style id="victimPin">');
      buffer.writeln('      <IconStyle>');
      buffer.writeln('        <color>ff0000ff</color>'); // Red in KML is AABBGGRR
      buffer.writeln('        <scale>1.2</scale>');
      buffer.writeln('        <Icon><href>http://maps.google.com/mapfiles/kml/pushpin/red-pushpin.png</href></Icon>');
      buffer.writeln('      </IconStyle>');
      buffer.writeln('    </Style>');

      // Iterar víctimas
      for (int i = 0; i < victims.length; i++) {
        var v = victims[i];
        double lat = v['lat'] ?? 0.0;
        double lng = v['lng'] ?? 0.0;
        if (lat == 0.0 && lng == 0.0) continue; // Skip invalid coordinates

        String status = v['isBreathing'] == true ? "VIVO (Respirando)" : "Actividad Detectada";
        
        buffer.writeln('    <Placemark>');
        buffer.writeln('      <name>Víctima #${i + 1}</name>');
        buffer.writeln('      <styleUrl>#victimPin</styleUrl>');
        buffer.writeln('      <description>');
        buffer.writeln('        Estado: $status\n');
        buffer.writeln('        BPM: ${v['bpm']}\n');
        buffer.writeln('        RPM: ${v['rpm']}\n');
        buffer.writeln('        Confianza: ${(v['confidence'] * 100).toStringAsFixed(1)}%\n');
        buffer.writeln('      </description>');
        buffer.writeln('      <Point>');
        buffer.writeln('        <coordinates>$lng,$lat,0</coordinates>');
        buffer.writeln('      </Point>');
        buffer.writeln('    </Placemark>');
      }

      // KML Footer
      buffer.writeln('  </Document>');
      buffer.writeln('</kml>');

      // Save to temp directory
      final directory = await getTemporaryDirectory();
      final file = File('${directory.path}/resqradar_rescate_${DateTime.now().millisecondsSinceEpoch}.kml');
      await file.writeAsString(buffer.toString());

      // Share file
      await Share.shareXFiles([XFile(file.path)], text: 'Mapa Táctico KML de Víctimas - ResQRadar');
      
    } catch (e) {
      print("Error exportando KML: $e");
    }
  }
}
