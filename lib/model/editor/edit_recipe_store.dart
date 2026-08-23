import 'dart:convert';
import 'dart:io';

import 'package:aves_model/aves_model.dart';
import 'package:flutter/foundation.dart';
import 'package:path/path.dart' as p;
import 'package:sqflite/sqflite.dart';

/// A recipe loaded from persistent storage together with source validation.
///
/// A changed source is intentionally not deleted automatically. The stale
/// recipe is user work and may still be recoverable/rebindable later.
@immutable
class StoredEditRecipe {
  final EditRecipe recipe;
  final bool sourceMatches;

  const StoredEditRecipe({
    required this.recipe,
    required this.sourceMatches,
  });
}

/// Durable storage for non-destructive image edits.
///
/// This deliberately uses a dedicated database instead of Aves' `metadata.db`.
/// Gallery/index metadata is rebuildable, while edit recipes are user-created
/// work and must survive metadata-cache resets.
class EditRecipeStore {
  static const _databaseName = 'editor.db';
  static const _databaseVersion = 1;
  static const _recipeTable = 'editRecipes';

  Database? _db;

  Future<String> get path async => p.join(await getDatabasesPath(), _databaseName);

  Future<Database> get _database async {
    final existing = _db;
    if (existing != null && existing.isOpen) return existing;

    final db = await openDatabase(
      await path,
      version: _databaseVersion,
      onCreate: (db, version) async {
        await db.execute(
          'CREATE TABLE $_recipeTable('
          'sourceUri TEXT PRIMARY KEY'
          ', entryId INTEGER'
          ', recipeJson TEXT NOT NULL'
          ', updatedMillis INTEGER NOT NULL'
          ')',
        );
        await db.execute('CREATE INDEX editRecipes_entryId ON $_recipeTable(entryId)');
      },
    );
    _db = db;
    return db;
  }

  /// Loads the recipe associated with [source.uri].
  ///
  /// [StoredEditRecipe.sourceMatches] verifies all source-identity fields, not
  /// only the URI, so callers can avoid applying a recipe to replaced pixels.
  Future<StoredEditRecipe?> load(EditSourceIdentity source) async {
    final db = await _database;
    final rows = await db.query(
      _recipeTable,
      columns: const ['recipeJson'],
      where: 'sourceUri = ?',
      whereArgs: [source.uri],
      limit: 1,
    );
    if (rows.isEmpty) return null;

    final recipeJson = rows.single['recipeJson'] as String?;
    if (recipeJson == null) return null;

    try {
      final decoded = jsonDecode(recipeJson);
      if (decoded is! Map) return null;
      final recipe = EditRecipe.fromJson(Map<String, Object?>.from(decoded));
      return StoredEditRecipe(
        recipe: recipe,
        sourceMatches: recipe.source == source,
      );
    } catch (error, stack) {
      // Do not delete the row. A future migration/recovery path may understand
      // data that this build cannot currently decode.
      debugPrint('$runtimeType failed to decode recipe for ${source.uri}: $error\n$stack');
      return null;
    }
  }

  Future<void> save({
    required int entryId,
    required EditRecipe recipe,
  }) async {
    final db = await _database;
    await db.insert(
      _recipeTable,
      {
        'sourceUri': recipe.source.uri,
        'entryId': entryId,
        'recipeJson': jsonEncode(recipe.toJson()),
        'updatedMillis': DateTime.now().millisecondsSinceEpoch,
      },
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  Future<void> delete(String sourceUri) async {
    final db = await _database;
    await db.delete(
      _recipeTable,
      where: 'sourceUri = ?',
      whereArgs: [sourceUri],
    );
  }

  Future<bool> contains(String sourceUri) async {
    final db = await _database;
    final rows = await db.query(
      _recipeTable,
      columns: const ['sourceUri'],
      where: 'sourceUri = ?',
      whereArgs: [sourceUri],
      limit: 1,
    );
    return rows.isNotEmpty;
  }

  Future<int> dbFileSize() async {
    final file = File(await path);
    return file.existsSync() ? file.length() : 0;
  }

  Future<void> close() async {
    final db = _db;
    _db = null;
    if (db != null && db.isOpen) {
      await db.close();
    }
  }
}
