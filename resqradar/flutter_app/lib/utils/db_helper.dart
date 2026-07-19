import 'package:sqflite/sqflite.dart';
import 'package:path/path.dart';

class DatabaseHelper {
  static final DatabaseHelper _instance = DatabaseHelper._internal();
  static Database? _database;

  factory DatabaseHelper() => _instance;

  DatabaseHelper._internal();

  Future<Database> get database async {
    if (_database != null) return _database!;
    _database = await _initDatabase();
    return _database!;
  }

  Future<Database> _initDatabase() async {
    String path = join(await getDatabasesPath(), 'vitalfi_history.db');
    return await openDatabase(
      path,
      version: 1,
      onCreate: (db, version) async {
        await db.execute('''
          CREATE TABLE search_history(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            timestamp TEXT,
            duration INTEGER,
            detections INTEGER,
            location_data TEXT
          )
        ''');
      },
    );
  }

  Future<int> insertHistory(Map<String, dynamic> data) async {
    Database db = await database;
    return await db.insert('search_history', data);
  }

  Future<List<Map<String, dynamic>>> getHistory() async {
    Database db = await database;
    return await db.query('search_history', orderBy: 'id DESC');
  }
}
