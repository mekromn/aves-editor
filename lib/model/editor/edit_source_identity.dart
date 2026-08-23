import 'package:aves/model/entry/entry.dart';
import 'package:aves_model/aves_model.dart';

extension EditSourceIdentityFromEntry on AvesEntry {
  EditSourceIdentity get editSourceIdentity => EditSourceIdentity(
        uri: uri,
        dateModifiedMillis: dateModifiedMillis ?? 0,
        sizeBytes: sizeBytes ?? 0,
        width: width,
        height: height,
        mimeType: mimeType,
      );
}
