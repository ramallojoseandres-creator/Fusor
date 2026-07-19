import 'dart:io';
import 'package:pdf/pdf.dart';
import 'package:pdf/widgets.dart' as pw;
import 'package:path_provider/path_provider.dart';
import 'package:share_plus/share_plus.dart';

class PdfReportService {
  static Future<void> generateAndShareReport(Map<String, dynamic> historyData) async {
    final pdf = pw.Document();

    pdf.addPage(
      pw.Page(
        pageFormat: PdfPageFormat.a4,
        build: (pw.Context context) {
          return pw.Column(
            crossAxisAlignment: pw.CrossAxisAlignment.start,
            children: [
              pw.Header(
                level: 0,
                child: pw.Text('ResQRadar - Reporte Medico de Rescate', 
                  style: pw.TextStyle(fontSize: 24, fontWeight: pw.FontWeight.bold, color: PdfColors.blue800)),
              ),
              pw.SizedBox(height: 20),
              pw.Text('Detalles de la Sesion', style: pw.TextStyle(fontSize: 18, fontWeight: pw.FontWeight.bold)),
              pw.SizedBox(height: 10),
              pw.Table.fromTextArray(
                context: context,
                data: <List<String>>[
                  ['Campo', 'Valor'],
                  ['ID de Sesion', historyData['id']?.toString() ?? 'N/A'],
                  ['Fecha/Hora', historyData['timestamp']?.toString() ?? 'N/A'],
                  ['Duracion (s)', historyData['duration']?.toString() ?? 'N/A'],
                  ['Detecciones de Vida', historyData['detections']?.toString() ?? 'N/A'],
                ],
              ),
              pw.SizedBox(height: 40),
              pw.Text('Observaciones:', style: pw.TextStyle(fontSize: 16, fontWeight: pw.FontWeight.bold)),
              pw.SizedBox(height: 10),
              pw.Container(
                width: double.infinity,
                height: 100,
                decoration: pw.BoxDecoration(border: pw.Border.all(color: PdfColors.grey300)),
                padding: const pw.EdgeInsets.all(10),
                child: pw.Text(
                  'Reporte generado automaticamente en la zona de desastre. Entregar al cuerpo de paramedicos para el triage primario.', 
                  style: const pw.TextStyle(color: PdfColors.grey700)
                ),
              )
            ],
          );
        },
      ),
    );

    final output = await getTemporaryDirectory();
    final String timestamp = DateTime.now().toIso8601String().replaceAll(':', '-').split('.')[0];
    final file = File('${output.path}/ResQRadar_Report_$timestamp.pdf');
    await file.writeAsBytes(await pdf.save());

    // Compartir o abrir el archivo (Menu de compartir nativo)
    await Share.shareXFiles([XFile(file.path)], text: 'Reporte de Rescate ResQRadar');
  }
}
