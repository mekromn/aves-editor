import 'package:aves/widgets/editor/recipe/controller.dart';
import 'package:aves_model/aves_model.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  const source = EditSourceIdentity(
    uri: 'content://media/external/images/media/42',
    dateModifiedMillis: 123456789,
    sizeBytes: 12000000,
    width: 4000,
    height: 3000,
    mimeType: 'image/jpeg',
  );

  test('recipe JSON round trip preserves unknown future operations', () {
    final recipe = EditRecipe(
      source: source,
      operations: [
        EditOperation(
          id: 'future-1',
          type: 'futureOperationAddedByNewerBuild',
          enabled: false,
          opacity: .75,
          maskId: 'mask-7',
          parameters: const {
            'amount': 0.25,
            'nested': {
              'mode': 'future',
              'values': [1, 2, 3],
            },
          },
        ),
      ],
    );

    final restored = EditRecipe.fromJson(recipe.toJson());

    expect(restored, recipe);
    expect(restored.operations.single.type, 'futureOperationAddedByNewerBuild');
    expect(restored.operations.single.parameters['nested'], isA<Map>());
  });

  test('recipe preserves ordered operation stack', () {
    final exposure = EditOperation(id: 'exposure-1', type: EditOperationTypes.exposure);
    final contrast = EditOperation(id: 'contrast-1', type: EditOperationTypes.contrast);
    final saturation = EditOperation(id: 'saturation-1', type: EditOperationTypes.saturation);

    final recipe = EditRecipe.empty(source)
        .append(exposure)
        .append(contrast)
        .append(saturation)
        .move('saturation-1', 1);

    expect(
      recipe.operations.map((v) => v.id),
      ['exposure-1', 'saturation-1', 'contrast-1'],
    );
  });

  test('controller supports undo and redo', () {
    final controller = EditRecipeController(recipe: EditRecipe.empty(source));

    controller.setScalar(EditOperationTypes.exposure, 1.0);
    controller.setScalar(EditOperationTypes.contrast, .25);

    expect(controller.recipe.operations.length, 2);
    expect(controller.canUndo, isTrue);

    controller.undo();
    expect(controller.recipe.operations.length, 1);
    expect(controller.canRedo, isTrue);

    controller.redo();
    expect(controller.recipe.operations.length, 2);
  });

  test('continuous interaction becomes one undo step', () {
    final controller = EditRecipeController(recipe: EditRecipe.empty(source));

    controller.beginInteraction();
    controller.setScalar(EditOperationTypes.exposure, .1);
    controller.setScalar(EditOperationTypes.exposure, .2);
    controller.setScalar(EditOperationTypes.exposure, .3);
    controller.endInteraction();

    expect(
      controller.recipe.firstOperationOfType(EditOperationTypes.exposure)?.parameters['amount'],
      .3,
    );

    controller.undo();
    expect(controller.recipe.isEmpty, isTrue);
  });

  test('duplicate gets a unique operation ID and stays adjacent', () {
    final controller = EditRecipeController(recipe: EditRecipe.empty(source));
    controller.setScalar(EditOperationTypes.structure, .4);

    final original = controller.recipe.operations.single;
    controller.duplicateOperation(original.id);

    expect(controller.recipe.operations.length, 2);
    expect(controller.recipe.operations[0].type, EditOperationTypes.structure);
    expect(controller.recipe.operations[1].type, EditOperationTypes.structure);
    expect(controller.recipe.operations[1].id, isNot(original.id));
  });
}
