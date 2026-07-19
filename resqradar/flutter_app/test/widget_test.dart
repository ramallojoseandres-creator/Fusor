import 'package:flutter_test/flutter_test.dart';
import 'package:vitalfiapps/main.dart';

void main() {
  testWidgets('ResQRadar app builds', (WidgetTester tester) async {
    await tester.pumpWidget(const ResQRadarApp());
    expect(find.byType(ResQRadarApp), findsOneWidget);
  });
}
