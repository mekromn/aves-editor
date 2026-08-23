import 'package:aves_model/aves_model.dart';
import 'package:flutter/foundation.dart';

/// Owns the in-memory non-destructive edit recipe and its undo/redo history.
///
/// Continuous gestures should call [beginInteraction] before the first update
/// and [endInteraction] when the pointer is released. This keeps a slider drag
/// as one undo step instead of hundreds of intermediate states.
class EditRecipeController extends ChangeNotifier {
  final int maxHistoryLength;

  EditRecipe _recipe;
  final List<EditRecipe> _undo = [];
  final List<EditRecipe> _redo = [];
  EditRecipe? _interactionStart;
  int _idSequence = 0;

  EditRecipeController(
    this._recipe, {
    this.maxHistoryLength = 100,
  });

  EditRecipe get recipe => _recipe;

  bool get canUndo => _undo.isNotEmpty;

  bool get canRedo => _redo.isNotEmpty;

  bool get interactionInProgress => _interactionStart != null;

  void beginInteraction() {
    _interactionStart ??= _recipe;
  }

  void endInteraction() {
    final start = _interactionStart;
    _interactionStart = null;
    if (start == null || start == _recipe) return;

    _pushUndo(start);
    _redo.clear();
    notifyListeners();
  }

  void cancelInteraction() {
    final start = _interactionStart;
    _interactionStart = null;
    if (start == null || start == _recipe) return;

    _recipe = start;
    notifyListeners();
  }

  void setRecipe(EditRecipe next, {bool recordHistory = true}) {
    if (next == _recipe) return;

    if (_interactionStart != null) {
      _recipe = next;
      notifyListeners();
      return;
    }

    if (recordHistory) {
      _pushUndo(_recipe);
      _redo.clear();
    }
    _recipe = next;
    notifyListeners();
  }

  /// Sets a scalar parameter on the first operation of [type], creating the
  /// operation at the end of the stack when it does not exist yet.
  ///
  /// The operation stays in the recipe when the value returns to its neutral
  /// value. This preserves user stack ordering and explicit enable/disable
  /// intent. A later cleanup command can remove neutral operations explicitly.
  void setScalar(
    String type,
    double value, {
    String parameter = 'amount',
  }) {
    final existing = _recipe.firstOperationOfType(type);
    if (existing == null) {
      final operation = EditOperation(
        id: _newOperationId(type),
        type: type,
        parameters: {parameter: value},
      );
      setRecipe(_recipe.append(operation));
      return;
    }

    setRecipe(
      _recipe.replace(
        existing.copyWith(
          parameters: {
            ...existing.parameters,
            parameter: value,
          },
        ),
      ),
    );
  }

  void setOperationEnabled(String operationId, bool enabled) {
    final operation = _recipe.operationById(operationId);
    if (operation == null || operation.enabled == enabled) return;
    setRecipe(_recipe.replace(operation.copyWith(enabled: enabled)));
  }

  void setOperationOpacity(String operationId, double opacity) {
    final operation = _recipe.operationById(operationId);
    if (operation == null) return;
    final clamped = opacity.clamp(0.0, 1.0).toDouble();
    if (operation.opacity == clamped) return;
    setRecipe(_recipe.replace(operation.copyWith(opacity: clamped)));
  }

  void setOperationMask(String operationId, String? maskId) {
    final operation = _recipe.operationById(operationId);
    if (operation == null || operation.maskId == maskId) return;
    setRecipe(_recipe.replace(operation.copyWith(maskId: maskId)));
  }

  void removeOperation(String operationId) {
    setRecipe(_recipe.remove(operationId));
  }

  void duplicateOperation(String operationId) {
    final operation = _recipe.operationById(operationId);
    if (operation == null) return;

    final sourceIndex = _recipe.operations.indexOf(operation);
    final duplicate = operation.copyWith(id: _newOperationId(operation.type));
    final operations = _recipe.operations.toList()..insert(sourceIndex + 1, duplicate);
    setRecipe(_recipe.copyWith(operations: operations));
  }

  void moveOperation(String operationId, int newIndex) {
    setRecipe(_recipe.move(operationId, newIndex));
  }

  void reset() {
    if (_recipe.isEmpty) return;
    setRecipe(EditRecipe.empty(_recipe.source));
  }

  void undo() {
    if (_undo.isEmpty) return;
    _interactionStart = null;
    _redo.add(_recipe);
    _recipe = _undo.removeLast();
    notifyListeners();
  }

  void redo() {
    if (_redo.isEmpty) return;
    _interactionStart = null;
    _pushUndo(_recipe);
    _recipe = _redo.removeLast();
    notifyListeners();
  }

  void replaceWithoutHistory(EditRecipe recipe) {
    _interactionStart = null;
    _undo.clear();
    _redo.clear();
    _recipe = recipe;
    notifyListeners();
  }

  void _pushUndo(EditRecipe recipe) {
    _undo.add(recipe);
    if (_undo.length > maxHistoryLength) {
      _undo.removeAt(0);
    }
  }

  String _newOperationId(String type) {
    _idSequence++;
    return '$type-${DateTime.now().microsecondsSinceEpoch}-$_idSequence';
  }
}
