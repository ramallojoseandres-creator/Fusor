import 'dart:async';
import 'package:sqflite/sqflite.dart';
import 'package:path/path.dart';

class DatabaseService {
  static final DatabaseService _instance = DatabaseService._internal();
  factory DatabaseService() => _instance;
  DatabaseService._internal();

  Database? _database;

  Future<Database> get database async {
    if (_database != null) return _database!;
    _database = await _initDatabase();
    return _database!;
  }

  Future<Database> _initDatabase() async {
    String path = join(await getDatabasesPath(), 'vitalfi_history.db');
    return await openDatabase(
      path,
      version: 2,
      onCreate: (db, version) {
        return db.execute(
          'CREATE TABLE history(id INTEGER PRIMARY KEY AUTOINCREMENT, timestamp TEXT, duration INTEGER, detections INTEGER, victims_json TEXT)',
        );
      },
      onUpgrade: (db, oldVersion, newVersion) async {
        if (oldVersion < 2) {
          await db.execute('ALTER TABLE history ADD COLUMN victims_json TEXT');
        }
      },
    );
  }

  Future<void> saveSession(int durationSeconds, int lifeDetections, {String? victimsJson}) async {
    final db = await database;
    await db.insert(
      'history',
      {
        'timestamp': DateTime.now().toIso8601String(),
        'duration': durationSeconds,
        'detections': lifeDetections,
        if (victimsJson != null) 'victims_json': victimsJson,
      },
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  Future<List<Map<String, dynamic>>> getHistory() async {
    final db = await database;
    return await db.query('history', orderBy: 'id DESC');
  }
}
