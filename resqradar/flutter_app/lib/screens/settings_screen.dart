import 'package:vitalfiapps/l10n/generated/app_localizations.dart';
import 'package:flutter/material.dart';

import '../main.dart';
import '../l10n/generated/app_localizations.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  double minFreq = 0.1;
  double maxFreq = 0.7;

  void _showEditDialog(String title, double currentValue, Function(double) onSave) {
    final controller = TextEditingController(text: currentValue.toString());
    showDialog(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: Text(title),
          content: TextField(
            controller: controller,
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
            decoration: const InputDecoration(hintText: "Ej. 0.3"),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: Text(AppLocalizations.of(context)!.settingsCancel),
            ),
            ElevatedButton(
              onPressed: () {
                final newValue = double.tryParse(controller.text);
                if (newValue != null) {
                  onSave(newValue);
                }
                Navigator.pop(context);
              },
              child: Text(AppLocalizations.of(context)!.settingsSave),
            ),
          ],
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(AppLocalizations.of(context)!.settingsTitle),
      ),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          _buildSectionHeader('Filtros de Frecuencia (Hz)'),
          ListTile(
            title: Text(AppLocalizations.of(context)!.settingsMinFreq),
            subtitle: Text('${minFreq.toStringAsFixed(1)} Hz'),
            trailing: const Icon(Icons.edit, color: Colors.grey),
            onTap: () {
              _showEditDialog('Editar Frecuencia Mínima', minFreq, (val) {
                setState(() => minFreq = val);
              });
            },
          ),
          ListTile(
            title: Text(AppLocalizations.of(context)!.settingsMaxFreq),
            subtitle: Text('${maxFreq.toStringAsFixed(1)} Hz'),
            trailing: const Icon(Icons.edit, color: Colors.grey),
            onTap: () {
              _showEditDialog('Editar Frecuencia Máxima', maxFreq, (val) {
                setState(() => maxFreq = val);
              });
            },
          ),
          const Divider(),
          _buildSectionHeader('Sensores'),
          SwitchListTile(
            title: Text(AppLocalizations.of(context)!.settingsMotionCancel),
            subtitle: Text(AppLocalizations.of(context)!.settingsMotionCancelDesc),
            value: true,
            activeColor: Theme.of(context).primaryColor,
            onChanged: (bool value) {},
          ),
          const Divider(),
          _buildSectionHeader('Apariencia Táctica'),
          ValueListenableBuilder<ThemeMode>(
            valueListenable: themeNotifier,
            builder: (context, currentMode, child) {
              return SwitchListTile(
                title: Text(AppLocalizations.of(context)!.settingsNightVision),
                subtitle: Text(AppLocalizations.of(context)!.settingsNightVisionDesc),
                value: currentMode == ThemeMode.dark,
                activeColor: Colors.red,
                secondary: const Icon(Icons.nightlight_round),
                onChanged: (bool value) {
                  themeNotifier.value = value ? ThemeMode.dark : ThemeMode.light;
                },
              );
            },
          ),
          SwitchListTile(
            title: Text(AppLocalizations.of(context)!.settingsBgScan),
            subtitle: Text(AppLocalizations.of(context)!.settingsBgScanDesc),
            value: true,
            activeColor: const Color(0xFF007BFF),
            onChanged: (bool value) {},
          ),
        ],
      ),
    );
  }

  Widget _buildSectionHeader(String title) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 12),
      child: Text(
        title,
        style: const TextStyle(
          color: Color(0xFF007BFF),
          fontWeight: FontWeight.bold,
          fontSize: 16,
        ),
      ),
    );
  }
}
