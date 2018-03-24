package org.ligerbots.powerup.commands;

import edu.wpi.first.wpilibj.command.Command;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import java.util.List;
import org.ligerbots.powerup.FieldPosition;
import org.ligerbots.powerup.FieldPosition.Action;
import org.ligerbots.powerup.Robot;
import org.ligerbots.powerup.RobotMap;
import org.ligerbots.powerup.RobotPosition;
import org.ligerbots.powerup.subsystems.DriveTrain;
import org.ligerbots.powerup.subsystems.DriveTrain.DriveSide;

/**
 *
 */
public class DrivePathCommand extends Command {
	
	int i = 0;

    List<FieldPosition> waypoints;
    int waypointIndex = 0;
    
    FieldPosition currentWaypoint;
    RobotPosition currentPosition;
    
    boolean lowerQuadrants;
    double angleError;
    double angleToWaypoint;
    
    boolean finished;
    
    double startAbsDistance;
    
    double turn;
    double drive;
    
    double rampUpDist;
    double rampDownDist;
    double rampUpDelta;
    double rampDownDelta;
    
    double oldDist;
    
    double dist;  
    double waypointDist;
    
    
    public DrivePathCommand(List<FieldPosition> waypoints) {
      requires (Robot.driveTrain);
      this.waypoints = waypoints;
        // Use requires() here to declare subsystem dependencies
        // eg. requires(chassis);
    }

    // Called just before this Command runs the first time
    protected void initialize() {
      
      finished = false;
      startAbsDistance = Robot.driveTrain.getAbsoluteDistanceTraveled();
      
      rampUpDist = 15.0;
      rampDownDist = 30.0;
      
      currentPosition = Robot.driveTrain.getRobotPosition();
      currentWaypoint = waypoints.get(waypointIndex);

      angleToWaypoint = Robot.driveTrain.getRobotPosition().angleTo(currentWaypoint);
      
      System.out.printf("ADC: WaypointIndex = %d, WaypointX = %5.2f, WaypointY = %5.2f, FinalTurn = %5.2f, Turn Output = %5.2f\n",
	  			waypointIndex, currentWaypoint.getX(), currentWaypoint.getY(), turn, Robot.driveTrain.getTurnOutput());
      
      dist = Robot.driveTrain.getRobotPosition().distanceTo(currentWaypoint);
      oldDist = Robot.driveTrain.getRobotPosition().distanceTo(currentWaypoint);
      
      waypointDist = Robot.driveTrain.getAbsoluteDistanceTraveled();
      
      Robot.elevator.setDesiredHeight(currentWaypoint.elevatorHeight);
    }

    // Called repeatedly when this Command is scheduled to run
    protected void execute() {
      
      
     // angleToWaypoint = Robot.driveTrain.getRobotPosition().angleTo(currentWaypoint);
          
      angleError = (waypoints.get(waypointIndex).action == Action.REVERSE) ? -90 - angleToWaypoint - Robot.driveTrain.getRobotPosition().getDirection() : 90 - angleToWaypoint - Robot.driveTrain.getRobotPosition().getDirection();
      
      if (angleError > 180) angleError -= 360;
      else if (angleError < -180) angleError += 360;
      
      
      turn = angleError * 0.01 + Math.signum(angleError) * 0.45;
      
      double rampUpDelta = Robot.driveTrain.getAbsoluteDistanceTraveled() - startAbsDistance;
      double rampDownDelta = currentPosition.distanceTo(waypoints.get(waypoints.size() - 1));
      
      if (Math.abs(angleError) >= 10) {
        System.out.println("Not Driving : " + angleError);
        drive = 0.0;
      } else {
          if (rampDownDelta < rampDownDist) {
            drive = (rampDownDelta * (0.4) / rampDownDist)
                + 0.5;
          } else/* (rampUpDelta < rampUpDist)*/ {
            drive = (Math.abs(rampUpDelta) * (0.4) / rampUpDist) + 0.6;
          }
          drive = (waypoints.get(waypointIndex).action == Action.REVERSE) ? drive * -1.0 : drive;
      }
      
      Robot.driveTrain.allDrive(drive, turn);
      
      SmartDashboard.putNumber("Drive Speed", drive);
      SmartDashboard.putNumber("Turn Speed", turn);
      SmartDashboard.putNumber("Angle Error", angleError);
      SmartDashboard.putNumber("Waypoint Index", waypointIndex);
      SmartDashboard.putNumber("Distance to Waypoint", currentPosition.distanceTo(currentWaypoint));
      SmartDashboard.putNumber("WaypointX", currentWaypoint.getX());
      SmartDashboard.putNumber("WaypointY", currentWaypoint.getY());
      
      if ((Robot.ticks % 1) == 0) {

    	  /*System.out.printf("X: %5.2f  Y: %5.2f Angle: %5.2f, Distance: %5.2f, Old Distance: %5.2f, Angle Error: %5.2f",
    			  Robot.driveTrain.getRobotPosition().getX(), Robot.driveTrain.getRobotPosition().getY(),
    			  Robot.driveTrain.getYaw(), currentPosition.distanceTo(currentWaypoint), oldDist, angleError);*/
        
          System.out.printf("X: %5.2f, Y: %5.2f, Dist: %5.2f, Distance Traveled: %5.2f, Angle: %5.2f, Angle Error: %5.2f, Drive: %5.2f, Turn: %5.2f \n", Robot.driveTrain.getRobotPosition().getX(), Robot.driveTrain.getRobotPosition().getY(), dist, Math.abs(Robot.driveTrain.getAbsoluteDistanceTraveled() - waypointDist)
              , Robot.driveTrain.getYaw(), angleError, drive, turn);
      }

      

      
      if (dist - Math.abs(Robot.driveTrain.getAbsoluteDistanceTraveled() - waypointDist) <= RobotMap.AUTO_DRIVE_DISTANCE_TOLERANCE/*(currentPosition.distanceTo(currentWaypoint) < RobotMap.AUTO_DRIVE_DISTANCE_TOLERANCE)
       || (Robot.driveTrain.getRobotPosition().distanceTo(currentWaypoint) - oldDist >= 0.1 && Math.abs(angleError) <= 10.0)*/) {
        
        
        
        if (waypointIndex == waypoints.size() - 1) {
          finished = true;
        }
        else {
                    
          waypointDist = Robot.driveTrain.getAbsoluteDistanceTraveled();
        
          Robot.driveTrain.allDrive(0, 0);
          
          waypointIndex += 1;
                    
          currentWaypoint = waypoints.get(waypointIndex);
          
          angleToWaypoint = Robot.driveTrain.getRobotPosition().angleTo(currentWaypoint);
          
          angleError = (waypoints.get(waypointIndex).action == Action.REVERSE) ? -90 - angleToWaypoint - Robot.driveTrain.getRobotPosition().getDirection() : 90 - angleToWaypoint - Robot.driveTrain.getRobotPosition().getDirection();
          
          dist = Robot.driveTrain.getRobotPosition().distanceTo(currentWaypoint);
          
          System.out.printf("ADC: WaypointIndex = %d, WaypointX = %5.2f, WaypointY = %5.2f, FinalTurn = %5.2f, Turn Output = %5.2f, Angle Error = %5.2f, Drive Speed = %5.2f\n",
  	  			waypointIndex, currentWaypoint.getX(), currentWaypoint.getY(), turn, Robot.driveTrain.getTurnOutput(), angleError, drive);
                    
        }
        
      }
      
      Robot.elevator.setDesiredHeight(currentWaypoint.elevatorHeight);
      
      oldDist = Robot.driveTrain.getRobotPosition().distanceTo(currentWaypoint);
	  
    }

    // Make this return true when this Command no longer needs to run execute()
    protected boolean isFinished() {
        return (waypointIndex >= (waypoints.size() - 1) && currentPosition.distanceTo(waypoints.get(waypoints.size() - 1)) < RobotMap.AUTO_DRIVE_DISTANCE_TOLERANCE) || finished;
    }

    // Called once after isFinished returns true
    protected void end() {
      Robot.driveTrain.disablePID();
      System.out.println("Finished");
    }
    
    // Called when another command which requires one or more of the same
    // subsystems is scheduled to run
    protected void interrupted() {
      Robot.driveTrain.disablePID();
    }
}
