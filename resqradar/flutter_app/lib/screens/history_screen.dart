import 'package:vitalfiapps/l10n/generated/app_localizations.dart';
import 'dart:convert';
import 'package:flutter/material.dart';

import '../services/pdf_report_service.dart';
import '../services/database_service.dart';

class HistoryScreen extends StatefulWidget {
  const HistoryScreen({super.key});

  @override
  State<HistoryScreen> createState() => _HistoryScreenState();
}

class _HistoryScreenState extends State<HistoryScreen> {
  final DatabaseService _dbService = DatabaseService();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(AppLocalizations.of(context)!.historyTitle),
      ),
      body: FutureBuilder<List<Map<String, dynamic>>>(
        future: _dbService.getHistory(),
        builder: (context, snapshot) {
          if (snapshot.connectionState == ConnectionState.waiting) {
            return const Center(child: CircularProgressIndicator());
          }
          if (!snapshot.hasData || snapshot.data!.isEmpty) {
            return Center(child: Text(AppLocalizations.of(context)!.historyEmpty));
          }
          
          final data = snapshot.data!;
          return ListView.builder(
            padding: const EdgeInsets.all(20),
            itemCount: data.length,
            itemBuilder: (context, index) {
              final item = data[index];
              final dateString = item['timestamp'].toString().split('T').first;
              final durationSec = item['duration'] as int;
              
              return Card(
                margin: const EdgeInsets.only(bottom: 16),
                elevation: 0,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(16),
                  side: BorderSide(color: Colors.grey.withOpacity(0.2)),
                ),
                child: ListTile(
                  contentPadding: const EdgeInsets.all(16),
                  leading: const CircleAvatar(
                    backgroundColor: Color(0xFFE3F2FD),
                    child: Icon(Icons.history, color: Color(0xFF007BFF)),
                  ),
                  title: Text(AppLocalizations.of(context)!.historyRescue(item['id'].toString())),
                  subtitle: Text('${AppLocalizations.of(context)!.historyDate(dateString)}\n${AppLocalizations.of(context)!.historyDuration(durationSec.toString())} | ${AppLocalizations.of(context)!.historyDetections(item['detections'].toString())}'),
                  trailing: IconButton(
                    icon: const Icon(Icons.picture_as_pdf, color: Colors.red),
                    onPressed: () {
                      PdfReportService.generateAndShareReport(item);
                    },
                  ),
                  isThreeLine: true,
                  onTap: () {
                    showDialog(
                      context: context,
                      builder: (context) {
                        return AlertDialog(
                          title: Text(AppLocalizations.of(context)!.historyDetailTitle(item['id'].toString())),
                          content: SingleChildScrollView(
                            child: ListBody(
                              children: <Widget>[
                                Text(AppLocalizations.of(context)!.historyDate(dateString), style: const TextStyle(fontWeight: FontWeight.bold)),
                                const SizedBox(height: 8),
                                Text(AppLocalizations.of(context)!.historyDuration(durationSec.toString())),
                                const SizedBox(height: 8),
                                Text(AppLocalizations.of(context)!.historyDetections(item['detections'].toString())),
                                const SizedBox(height: 16),
                                if (item['victims_json'] != null)
                                  ..._buildVictimsList(item['victims_json']),
                              ],
                            ),
                          ),
                          actions: <Widget>[
                            TextButton(
                              child: Text(AppLocalizations.of(context)!.btnClose),
                              onPressed: () {
                                Navigator.of(context).pop();
                              },
                            ),
                          ],
                        );
                      },
                    );
                  },
                ),
              );
            },
          );
        },
      ),
    );
  }

  List<Widget> _buildVictimsList(String jsonString) {
    try {
      final List<dynamic> victims = jsonDecode(jsonString);
      if (victims.isEmpty) return [Text(AppLocalizations.of(context)!.historyDetailVictims)];
      
      return [
        Text(AppLocalizations.of(context)!.historyDetailVictims, style: TextStyle(fontWeight: FontWeight.bold, color: Colors.blue)),
        const SizedBox(height: 8),
        ...victims.map((v) {
          final isCritical = v['isCritical'] == true;
          return Container(
            margin: const EdgeInsets.only(bottom: 8),
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              color: isCritical ? Colors.red.withOpacity(0.1) : Colors.green.withOpacity(0.1),
              borderRadius: BorderRadius.circular(8),
              border: Border.all(color: isCritical ? Colors.red : Colors.green),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(AppLocalizations.of(context)!.historyVictim(v['id'].toString()), style: const TextStyle(fontWeight: FontWeight.bold)),
                Text('${AppLocalizations.of(context)!.lblDistance}: ${v['distance']?.toStringAsFixed(1)}m'),
                Text(AppLocalizations.of(context)!.historyHeart(v['bpm']?.toStringAsFixed(0) ?? '0')),
                Text(AppLocalizations.of(context)!.historyBreath(v['rpm']?.toStringAsFixed(0) ?? '0')),
              ],
            ),
          );
        }).toList(),
      ];
    } catch (e) {
      return [Text(AppLocalizations.of(context)!.historyError(e.toString()), style: const TextStyle(color: Colors.red))];
    }
  }
}
