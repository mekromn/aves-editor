import 'dart:async';

import 'package:aves/model/editor/edit_recipe_store.dart';
import 'package:aves/model/editor/edit_source_identity.dart';
import 'package:aves/model/entry/entry.dart';
import 'package:aves/model/settings/settings.dart';
import 'package:aves/model/viewer/view_state.dart';
import 'package:aves/widgets/editor/control_panel.dart';
import 'package:aves/widgets/editor/image.dart';
import 'package:aves/widgets/editor/recipe/controller.dart';
import 'package:aves/widgets/editor/transform/controller.dart';
import 'package:aves/widgets/editor/transform/cropper.dart';
import 'package:aves/widgets/viewer/overlay/top/minimap.dart';
import 'package:aves_magnifier/aves_magnifier.dart';
import 'package:aves_model/aves_model.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

class ImageEditorPage extends StatefulWidget {
  static const routeName = '/image_editor';

  final AvesEntry entry;

  const ImageEditorPage({
    super.key,
    required this.entry,
  });

  @override
  State<ImageEditorPage> createState() => _ImageEditorPageState();
}

class _ImageEditorPageState extends State<ImageEditorPage> {
  final Set<StreamSubscription> _subscriptions = {};
  final ValueNotifier<EditorAction?> _actionNotifier = ValueNotifier(null);
  final ValueNotifier<EdgeInsets> _marginNotifier = ValueNotifier(EdgeInsets.zero);
  final ValueNotifier<ViewState> _viewStateNotifier = ValueNotifier<ViewState>(ViewState.zero);
  final AvesMagnifierController _magnifierController = AvesMagnifierController();
  late final TransformController _transformController;
  late final EditRecipeController _editRecipeController;
  late final EditRecipeStore _editRecipeStore;
  Timer? _recipeSaveDebounce;
  bool _recipeLoadComplete = false;

  @override
  void initState() {
    super.initState();
    _transformController = TransformController(widget.entry.displaySize);
    _editRecipeStore = EditRecipeStore();
    _editRecipeController = EditRecipeController(
      EditRecipe.empty(widget.entry.editSourceIdentity),
    )..addListener(_onEditRecipeChanged);

    _actionNotifier.addListener(_onActionChanged);
    _subscriptions.add(_transformController.transformationStream.map((v) => v.matrix).distinct().listen(_onTransformationMatrixChanged));
    unawaited(_loadEditRecipe());
  }

  @override
  void dispose() {
    _recipeSaveDebounce?.cancel();
    _editRecipeController.removeListener(_onEditRecipeChanged);
    unawaited(_flushAndCloseRecipeStore());
    _subscriptions
      ..forEach((sub) => sub.cancel())
      ..clear();
    _actionNotifier.dispose();
    _marginNotifier.dispose();
    _viewStateNotifier.dispose();
    _magnifierController.dispose();
    _transformController.dispose();
    _editRecipeController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: MultiProvider(
        providers: [
          Provider<AvesMagnifierController>.value(value: _magnifierController),
          Provider<TransformController>.value(value: _transformController),
          ChangeNotifierProvider<EditRecipeController>.value(value: _editRecipeController),
        ],
        child: SafeArea(
          child: Column(
            children: [
              Expanded(
                child: Stack(
                  children: [
                    ClipRect(
                      child: EditorImage(
                        magnifierController: _magnifierController,
                        transformController: _transformController,
                        actionNotifier: _actionNotifier,
                        marginNotifier: _marginNotifier,
                        viewStateNotifier: _viewStateNotifier,
                        entry: widget.entry,
                      ),
                    ),
                    if (settings.showOverlayMinimap)
                      PositionedDirectional(
                        start: 8,
                        bottom: 8,
                        child: Minimap(viewStateNotifier: _viewStateNotifier),
                      ),
                    ValueListenableBuilder<EditorAction?>(
                      valueListenable: _actionNotifier,
                      builder: (context, action, child) {
                        switch (action) {
                          case .transform:
                            return Cropper(
                              magnifierController: _magnifierController,
                              transformController: _transformController,
                              marginNotifier: _marginNotifier,
                            );
                          case null:
                            return const SizedBox();
                        }
                      },
                    ),
                  ],
                ),
              ),
              const Divider(height: 0),
              EditorControlPanel(
                entry: widget.entry,
                actionNotifier: _actionNotifier,
              ),
            ],
          ),
        ),
      ),
      resizeToAvoidBottomInset: false,
    );
  }

  Future<void> _loadEditRecipe() async {
    final source = widget.entry.editSourceIdentity;
    final stored = await _editRecipeStore.load(source);
    if (!mounted) return;

    if (stored != null) {
      if (stored.sourceMatches) {
        _editRecipeController.replaceWithoutHistory(stored.recipe);
      } else {
        // Do not apply a recipe whose source fingerprint changed. Keep it in
        // editor.db for a future recovery/rebind UI instead of deleting work.
        debugPrint('$runtimeType found stale edit recipe for ${source.uri}; preserving without applying');
      }
    }
    _recipeLoadComplete = true;
  }

  void _onEditRecipeChanged() {
    if (!_recipeLoadComplete) return;
    _recipeSaveDebounce?.cancel();
    _recipeSaveDebounce = Timer(const Duration(milliseconds: 250), () {
      unawaited(_saveEditRecipe());
    });
  }

  Future<void> _saveEditRecipe() async {
    if (!_recipeLoadComplete) return;
    await _editRecipeStore.save(
      entryId: widget.entry.id,
      recipe: _editRecipeController.recipe,
    );
  }

  Future<void> _flushAndCloseRecipeStore() async {
    if (_recipeLoadComplete) {
      await _saveEditRecipe();
    }
    await _editRecipeStore.close();
  }

  void _onActionChanged() {
    switch (_actionNotifier.value) {
      case .transform:
        _transformController.reset();
        _marginNotifier.value = Cropper.imageMargin;
      default:
        _marginNotifier.value = EdgeInsets.zero;
    }
  }

  void _onTransformationMatrixChanged(Matrix4 transformationMatrix) {
    final boundaries = _magnifierController.scaleBoundaries;
    if (boundaries != null) {
      _magnifierController.setScaleBoundaries(
        boundaries.copyWith(
          externalTransform: transformationMatrix,
        ),
      );
    }
  }
}
