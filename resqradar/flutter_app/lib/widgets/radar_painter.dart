import 'dart:math';
import 'package:flutter/material.dart';

class DetectedPerson {
  final String id;
  final double distance; // in meters
  final double angle; // in radians
  final double bpm;
  final double rpm;
  final bool isCritical;

  DetectedPerson({
    required this.id,
    required this.distance,
    required this.angle,
    required this.bpm,
    required this.rpm,
    this.isCritical = false,
  });
}

class RadarPainter extends CustomPainter {
  final double scanAngle;
  final List<DetectedPerson> detections;
  final Color baseColor;

  RadarPainter({
    required this.scanAngle,
    required this.detections,
    required this.baseColor,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final radius = min(size.width / 2, size.height / 2);
    
    // Draw grid rings
    final Paint gridPaint = Paint()
      ..color = baseColor.withOpacity(0.2)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.0;
      
    canvas.drawCircle(center, radius, gridPaint);
    canvas.drawCircle(center, radius * 0.66, gridPaint);
    canvas.drawCircle(center, radius * 0.33, gridPaint);
    
    // Crosshairs
    canvas.drawLine(Offset(center.dx, 0), Offset(center.dx, size.height), gridPaint);
    canvas.drawLine(Offset(0, center.dy), Offset(size.width, center.dy), gridPaint);

    // Draw expanding sonar pulse (concentric waves)
    // scanAngle ranges from 0 to 2*pi
    double progress = scanAngle / (2 * pi);
    
    // Wave 1
    double wave1Radius = radius * progress;
    final wave1Paint = Paint()
      ..color = baseColor.withOpacity(1.0 - progress)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 3.0;
    canvas.drawCircle(center, wave1Radius, wave1Paint);
    
    // Wave 2 (offset by 0.5)
    double progress2 = (progress + 0.5) % 1.0;
    double wave2Radius = radius * progress2;
    final wave2Paint = Paint()
      ..color = baseColor.withOpacity(1.0 - progress2)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 3.0;
    canvas.drawCircle(center, wave2Radius, wave2Paint);

    // Draw detected persons
    final maxDistance = 5.0; // Max radar range is 5 meters
    for (var person in detections) {
      // Calculate dot position based on distance and angle
      final normalizedDist = min(person.distance / maxDistance, 1.0);
      final r = radius * normalizedDist;
      
      final dotX = center.dx + r * cos(person.angle);
      final dotY = center.dy + r * sin(person.angle);
      final dotCenter = Offset(dotX, dotY);
      
      // Glow effect if critical
      if (person.isCritical) {
        final glowPaint = Paint()
          ..color = Colors.red.withOpacity(0.4)
          ..style = PaintingStyle.fill;
        canvas.drawCircle(dotCenter, 8.0, glowPaint);
      }
      
      final dotPaint = Paint()
        ..color = person.isCritical ? Colors.red : Colors.greenAccent
        ..style = PaintingStyle.fill;
        
      canvas.drawCircle(dotCenter, 4.0, dotPaint);
    }
  }

  @override
  bool shouldRepaint(covariant RadarPainter oldDelegate) {
    return oldDelegate.scanAngle != scanAngle || oldDelegate.detections != detections;
  }
}
