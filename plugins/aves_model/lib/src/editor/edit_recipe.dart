import 'package:collection/collection.dart';
import 'package:flutter/foundation.dart';

/// Stable wire names for edit operations understood by this build.
///
/// The serialized recipe intentionally stores operation types as strings instead
/// of an enum. That allows an older build to load, preserve and re-save an
/// operation created by a newer build without destroying unknown future data.
abstract final class EditOperationTypes {
  static const transform = 'transform';

  static const exposure = 'exposure';
  static const brightness = 'brightness';
  static const contrast = 'contrast';
  static const highlights = 'highlights';
  static const shadows = 'shadows';
  static const whites = 'whites';
  static const blacks = 'blacks';
  static const ambiance = 'ambiance';
  static const tonalContrast = 'tonalContrast';

  static const temperature = 'temperature';
  static const tint = 'tint';
  static const saturation = 'saturation';
  static const vibrance = 'vibrance';
  static const skinTone = 'skinTone';
  static const blueTone = 'blueTone';
  static const adaptiveColor = 'adaptiveColor';
  static const gamutExpansion = 'gamutExpansion';

  static const structure = 'structure';
  static const sharpening = 'sharpening';
  static const fineDetail = 'fineDetail';
  static const mediumDetail = 'mediumDetail';
  static const coarseDetail = 'coarseDetail';
  static const denoise = 'denoise';

  static const curves = 'curves';
}

/// Identity of the source media at the time an edit recipe was created/saved.
///
/// Entry IDs alone are not sufficient because MediaStore rows and paths can be
/// replaced or reused. These fields let the persistence layer detect that a
/// recipe may no longer describe the same source pixels.
@immutable
class EditSourceIdentity {
  final String uri;
  final int dateModifiedMillis;
  final int sizeBytes;
  final int width;
  final int height;
  final String? mimeType;

  const EditSourceIdentity({
    required this.uri,
    required this.dateModifiedMillis,
    required this.sizeBytes,
    required this.width,
    required this.height,
    this.mimeType,
  });

  factory EditSourceIdentity.fromJson(Map<String, Object?> json) {
    return EditSourceIdentity(
      uri: json['uri'] as String,
      dateModifiedMillis: (json['dateModifiedMillis'] as num).toInt(),
      sizeBytes: (json['sizeBytes'] as num).toInt(),
      width: (json['width'] as num).toInt(),
      height: (json['height'] as num).toInt(),
      mimeType: json['mimeType'] as String?,
    );
  }

  Map<String, Object?> toJson() => {
        'uri': uri,
        'dateModifiedMillis': dateModifiedMillis,
        'sizeBytes': sizeBytes,
        'width': width,
        'height': height,
        if (mimeType != null) 'mimeType': mimeType,
      };

  @override
  bool operator ==(Object other) {
    return other is EditSourceIdentity &&
        other.uri == uri &&
        other.dateModifiedMillis == dateModifiedMillis &&
        other.sizeBytes == sizeBytes &&
        other.width == width &&
        other.height == height &&
        other.mimeType == mimeType;
  }

  @override
  int get hashCode => Object.hash(uri, dateModifiedMillis, sizeBytes, width, height, mimeType);
}

/// One ordered, non-destructive operation in an [EditRecipe].
///
/// [parameters] must contain only JSON-compatible values. Keeping the payload
/// generic is intentional: operation-specific schemas can evolve independently
/// while the recipe container remains forward compatible.
@immutable
class EditOperation {
  static const _unset = Object();
  static const _deepEquality = DeepCollectionEquality();

  final String id;
  final String type;
  final bool enabled;
  final double opacity;
  final String? maskId;
  final Map<String, Object?> parameters;

  EditOperation({
    required this.id,
    required this.type,
    this.enabled = true,
    this.opacity = 1.0,
    this.maskId,
    Map<String, Object?> parameters = const {},
  })  : assert(id != ''),
        assert(type != ''),
        assert(opacity >= 0 && opacity <= 1),
        parameters = Map.unmodifiable(parameters);

  factory EditOperation.fromJson(Map<String, Object?> json) {
    final parametersJson = json['parameters'];
    return EditOperation(
      id: json['id'] as String,
      type: json['type'] as String,
      enabled: json['enabled'] as bool? ?? true,
      opacity: (json['opacity'] as num?)?.toDouble() ?? 1.0,
      maskId: json['maskId'] as String?,
      parameters: parametersJson is Map
          ? Map<String, Object?>.from(parametersJson)
          : const {},
    );
  }

  EditOperation copyWith({
    String? id,
    String? type,
    bool? enabled,
    double? opacity,
    Object? maskId = _unset,
    Map<String, Object?>? parameters,
  }) {
    return EditOperation(
      id: id ?? this.id,
      type: type ?? this.type,
      enabled: enabled ?? this.enabled,
      opacity: opacity ?? this.opacity,
      maskId: identical(maskId, _unset) ? this.maskId : maskId as String?,
      parameters: parameters ?? this.parameters,
    );
  }

  Map<String, Object?> toJson() => {
        'id': id,
        'type': type,
        'enabled': enabled,
        'opacity': opacity,
        if (maskId != null) 'maskId': maskId,
        'parameters': parameters,
      };

  @override
  bool operator ==(Object other) {
    return other is EditOperation &&
        other.id == id &&
        other.type == type &&
        other.enabled == enabled &&
        other.opacity == opacity &&
        other.maskId == maskId &&
        _deepEquality.equals(other.parameters, parameters);
  }

  @override
  int get hashCode => Object.hash(
        id,
        type,
        enabled,
        opacity,
        maskId,
        _deepEquality.hash(parameters),
      );
}

/// Versioned, ordered description of every non-destructive edit applied to a
/// source image.
@immutable
class EditRecipe {
  static const currentSchemaVersion = 1;
  static const _listEquality = ListEquality<EditOperation>();

  final int schemaVersion;
  final EditSourceIdentity source;
  final List<EditOperation> operations;

  EditRecipe({
    this.schemaVersion = currentSchemaVersion,
    required this.source,
    List<EditOperation> operations = const [],
  })  : assert(schemaVersion > 0),
        operations = List.unmodifiable(operations);

  factory EditRecipe.empty(EditSourceIdentity source) => EditRecipe(source: source);

  factory EditRecipe.fromJson(Map<String, Object?> json) {
    final operationsJson = json['operations'];
    return EditRecipe(
      schemaVersion: (json['schemaVersion'] as num?)?.toInt() ?? 1,
      source: EditSourceIdentity.fromJson(
        Map<String, Object?>.from(json['source'] as Map),
      ),
      operations: operationsJson is List
          ? operationsJson
              .whereType<Map>()
              .map((v) => EditOperation.fromJson(Map<String, Object?>.from(v)))
              .toList(growable: false)
          : const [],
    );
  }

  bool get isEmpty => operations.isEmpty;

  Iterable<EditOperation> get enabledOperations => operations.where((v) => v.enabled && v.opacity > 0);

  EditOperation? operationById(String id) => operations.firstWhereOrNull((v) => v.id == id);

  EditOperation? firstOperationOfType(String type) => operations.firstWhereOrNull((v) => v.type == type);

  EditRecipe copyWith({
    int? schemaVersion,
    EditSourceIdentity? source,
    List<EditOperation>? operations,
  }) {
    return EditRecipe(
      schemaVersion: schemaVersion ?? this.schemaVersion,
      source: source ?? this.source,
      operations: operations ?? this.operations,
    );
  }

  EditRecipe append(EditOperation operation) {
    if (operationById(operation.id) != null) {
      throw ArgumentError.value(operation.id, 'operation.id', 'Operation IDs must be unique inside a recipe.');
    }
    return copyWith(operations: [...operations, operation]);
  }

  EditRecipe replace(EditOperation operation) {
    final index = operations.indexWhere((v) => v.id == operation.id);
    if (index < 0) return append(operation);

    final next = operations.toList();
    next[index] = operation;
    return copyWith(operations: next);
  }

  EditRecipe remove(String operationId) {
    final next = operations.where((v) => v.id != operationId).toList(growable: false);
    return next.length == operations.length ? this : copyWith(operations: next);
  }

  EditRecipe move(String operationId, int newIndex) {
    final oldIndex = operations.indexWhere((v) => v.id == operationId);
    if (oldIndex < 0) return this;

    final next = operations.toList();
    final operation = next.removeAt(oldIndex);
    final clampedIndex = newIndex.clamp(0, next.length).toInt();
    next.insert(clampedIndex, operation);
    return copyWith(operations: next);
  }

  Map<String, Object?> toJson() => {
        'schemaVersion': schemaVersion,
        'source': source.toJson(),
        'operations': operations.map((v) => v.toJson()).toList(growable: false),
      };

  @override
  bool operator ==(Object other) {
    return other is EditRecipe &&
        other.schemaVersion == schemaVersion &&
        other.source == source &&
        _listEquality.equals(other.operations, operations);
  }

  @override
  int get hashCode => Object.hash(schemaVersion, source, _listEquality.hash(operations));
}
