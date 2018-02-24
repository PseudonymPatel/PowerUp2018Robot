package org.ligerbots.powerup.subsystems;

import com.ctre.phoenix.motorcontrol.ControlMode;
import com.ctre.phoenix.motorcontrol.FeedbackDevice;
import com.ctre.phoenix.motorcontrol.NeutralMode;
import com.ctre.phoenix.motorcontrol.can.WPI_TalonSRX;
import com.kauailabs.navx.AHRSProtocol.AHRSUpdateBase;
import com.kauailabs.navx.frc.AHRS;

import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.PIDController;
import edu.wpi.first.wpilibj.SerialPort;
import edu.wpi.first.wpilibj.SPI.Port;
import edu.wpi.first.wpilibj.SpeedControllerGroup;
import edu.wpi.first.wpilibj.command.Subsystem;
import edu.wpi.first.wpilibj.drive.DifferentialDrive;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import java.util.Arrays;

import org.ligerbots.powerup.Robot;
import org.ligerbots.powerup.RobotMap;
import org.ligerbots.powerup.commands.DriveCommand;
import org.ligerbots.powerup.commands.DriveDistance;
import org.ligerbots.powerup.RobotPosition;

/**
 *
 */
public class DriveTrain extends Subsystem {

  // Put methods for controlling this subsystem
  // here. Call these from Commands.
  WPI_TalonSRX leftMaster;
  WPI_TalonSRX leftSlave;
  WPI_TalonSRX rightMaster;
  WPI_TalonSRX rightSlave;
  SpeedControllerGroup left;
  SpeedControllerGroup right;
  DifferentialDrive robotDrive;
  PIDController turningController;
  double turnOutput = 0;
  double angleOffset = 0;
  TalonID[] talons;

  public enum DriveSide {
    LEFT, RIGHT
  }

  private AHRS navx;

  double positionX;
  double positionY;
  double rotation;
  double absoluteDistanceTraveled;
  int numberOfTicks = 0;
  
  double prevEncoderLeft;
  double prevEncoderRight;
  double rotationOffset = 0;
  double lastOutputLeft = 0;
  double lastOutputRight = 0;
  
  RobotPosition robotPosition;

  public class TalonID {
    int talonID;
    WPI_TalonSRX talon;

    public TalonID(int talonID, WPI_TalonSRX talon) {
      this.talonID = talonID;
      this.talon = talon;
    }
  }

  @SuppressWarnings("unused")
public DriveTrain() {
	System.out.println("DriveTrain constructed");

    SmartDashboard.putNumber("Elevator Up Accel", 2);
    SmartDashboard.putNumber("Elevator Up Speed", 0.25);

    
    // This initial robot position will be overwritten by our autonomous selection
    // we only zero it out here for practice, where we go straight teleop
    robotPosition = new RobotPosition(0.0, 0.0, 0.0);

    leftMaster = new WPI_TalonSRX(RobotMap.CT_LEFT_1);
    leftSlave = new WPI_TalonSRX(RobotMap.CT_LEFT_2);
    rightMaster = new WPI_TalonSRX(RobotMap.CT_RIGHT_1);
    rightSlave = new WPI_TalonSRX(RobotMap.CT_RIGHT_2);

    // leftMaster.configSelectedFeedbackSensor(FeedbackDevice.QuadEncoder, 0, 0);

    leftSlave.set(ControlMode.Follower, leftMaster.getDeviceID());
    rightSlave.set(ControlMode.Follower, rightMaster.getDeviceID());

    // left = new SpeedControllerGroup(leftMaster, leftSlave);
    // right = new SpeedControllerGroup(rightMaster, rightSlave);

    leftMaster.configSelectedFeedbackSensor(FeedbackDevice.QuadEncoder, 0, 0);
    rightMaster.configSelectedFeedbackSensor(FeedbackDevice.QuadEncoder, 0, 0);

    rightMaster.setSensorPhase(true);
    leftMaster.setSensorPhase(true);
    
    leftMaster.configClosedloopRamp(0.3, 0);
    rightMaster.configClosedloopRamp(0.3, 0);

    Arrays.asList(leftMaster, rightMaster, leftSlave, rightSlave)
        .forEach((WPI_TalonSRX talon) -> talon.setNeutralMode(NeutralMode.Brake));

    robotDrive = new DifferentialDrive(leftMaster, rightMaster);

    // TODO: This should be sampled at 200Hz
    // until we get the navX fixed, but use the elevator being present as an indication that this is NOT the H-Drive bot
    if (Robot.elevator.elevatorPresent) {
    	navx = new AHRS(Port.kMXP, (byte) 50);
    	System.out.println("NavX on MXP port.");
    	}
    else {
    	navx = new AHRS(SerialPort.Port.kUSB);
    	System.out.println("NavX on USB port.");
    }
   
	turningController =
        new PIDController(SmartDashboard.getNumber("DriveP", 0.045), SmartDashboard.getNumber("DriveI", 0.004),
         			      SmartDashboard.getNumber("DriveD", 0.06), navx, output -> this.turnOutput = output);

	navx.registerCallback(
        (long systemTimestamp, long sensorTimestamp, AHRSUpdateBase sensorData, Object context) -> {
          updatePosition(sensorData.yaw);
 /*         turningController.setP(SmartDashboard.getNumber("DriveP", 1));
          turningController.setI(SmartDashboard.getNumber("DriveI", 0.01));
          turningController.setD(SmartDashboard.getNumber("DriveD", 0.5));*/
        }, new Object());


	turningController =
			new PIDController(0.05, 0.005, 0.05, navx, output -> this.turnOutput = output);

    //calibrateYaw();
  }
  
  public void setInitialRobotPosition(double x, double y, double angle)
  {
	  robotPosition.setRobotPosition(x, y, angle);
  }
  
  public double getPitch() {
	  return navx.getPitch();
  }
  
  public double getRoll() {
 return navx.getRoll();
  }

  public void talonCurrent() {
    Arrays.asList(leftMaster, rightMaster, leftSlave, rightSlave)
        .forEach((WPI_TalonSRX talon) -> SmartDashboard
            .putNumber(((Integer) talon.getDeviceID()).toString(), talon.getOutputCurrent()));
  }
  
  public void zeroEncoders() {
    leftMaster.setSelectedSensorPosition(0, 0, 0);
    rightMaster.setSelectedSensorPosition(0, 0, 0);

  }


  double rampRate;

  public void allDrive(double throttle, double rotate) {

    // rampRate = SmartDashboard.getNumber("Strafe Ramp Rate", 0.3);
	  // TODO: Add autobalancing here. Adjust throttle based on pitch and rotate based on roll.
    if (Robot.elevator.getPosition() < 50) {
      leftMaster.configOpenloopRamp(0, 0);
      rightMaster.configOpenloopRamp(0, 0);
      robotDrive.arcadeDrive(-throttle, -rotate);
    }
    else {
      leftMaster.configOpenloopRamp(SmartDashboard.getNumber("Elevator Up Accel", 2), 0);
      rightMaster.configOpenloopRamp(SmartDashboard.getNumber("Elevator Up Accel", 2), 0);
      robotDrive.arcadeDrive(-throttle * SmartDashboard.getNumber("Elevator Up Speed", 0.25), -rotate);
    }
  }

  // Returns the current yaw value (in degrees, from -180 to 180)
  public double getYaw() {
	  return navx.getYaw();
  }

  // Return the rate of rotation of the yaw (Z-axis) gyro, in degrees per second.
  public double getRate() {
	  return navx.getRate();
  }

  // Returns the total accumulated yaw angle (Z Axis, in degrees)
  // reported by the sensor since it was last zeroed. This will go beyond 360 degrees.
  public double getAngle() {
      return navx.getAngle();
  }

  public void initDefaultCommand() {
	if (Robot.driveCommand == null) Robot.driveCommand = new DriveCommand();
    setDefaultCommand(Robot.driveCommand);
  }

  public double getEncoderDistance(DriveSide driveSide) {
    if (driveSide == DriveSide.LEFT) {
      return (leftSlave.getSelectedSensorPosition(0) / 1024.0) * RobotMap.GEARING_FACTOR
          * RobotMap.WHEEL_CIRCUMFERENCE;
    } else {
      return (-rightSlave.getSelectedSensorPosition(0) / 1024.0) * RobotMap.GEARING_FACTOR
          * RobotMap.WHEEL_CIRCUMFERENCE;
    }
  }

  public void printEncoder() {
    SmartDashboard.putNumber("Left Encoder",
        leftMaster.getSelectedSensorPosition(0) / 1024.0 * RobotMap.WHEEL_CIRCUMFERENCE);
    SmartDashboard.putNumber("Right Encoder",
        rightMaster.getSelectedSensorPosition(0) / 1024.0 * RobotMap.WHEEL_CIRCUMFERENCE);
    //SmartDashboard.putData("Drive Distance Command", new DriveDistance(512.0, 1.00, 1.0));
  }

  // TODO: Can we refactor this to remove the "temporary" from its name?
  double temporaryFixDegrees(double input) {
    if (input > 180) {
      return input - 360;
    } else if (input < -180) {
      return input + 360;
    } else {
      return input;
    }
  }
  public void enableTurningControl(double angle, double tolerance) {
    double startAngle = this.getYaw();
    double temp = startAngle + angle;
    // RobotMap.TURN_P = turningController.getP();
    // RobotMap.TURN_D = turningController.getD();
    // RobotMap.TURN_I = turningController.getI();
    temp = temporaryFixDegrees(temp);
    turningController.setSetpoint(temp);
    turningController.enable();
    turningController.setInputRange(-180.0, 180.0);
    turningController.setOutputRange(-1.0, 1.0);
    turningController.setAbsoluteTolerance(tolerance);
    // turningController.setToleranceBuffer(1);
    turningController.setContinuous(true);
    turningController.setSetpoint(temp);
    
    System.out.printf("startAngle: %5.2f, Yaw: %5.2f, setPoint: %5.2f, "
    		+ startAngle, getYaw(), temp);
  }
  
  public boolean isTurnOnTarget() {

	  if (Math.abs(turnError()) <= 4) {
		  ++numberOfTicks;
		  if (numberOfTicks >= 15) {
			  numberOfTicks = 0;
			  return true;
		  }
	  }
	  else {
		  numberOfTicks = 0;
	  }
	  return false;
    //return turningController.onTarget();
  }


  public void setPID(double p, double i, double d) {
	  turningController.setPID(p, i, d);
  }

  public void disablePID() {
	  turningController.disable();
  }

  public boolean isPidOn() {
	  return turningController.isEnabled();
  }
  public void setAngleOffset(double angleOffset) {
	  this.angleOffset = angleOffset;
  }
  public double getTurnOutput() {
	  angleOffset = turnError();
	  //System.out.println(turnError());
	  
	  if (angleOffset > 75) {
		  return -1.0;
	  }
	  if (angleOffset > 45) {
		  return -0.75;
	  }
	  if (angleOffset > 20) {
		  return -0.4;
	  }
	  if (angleOffset > 4) {
		  return -0.2;
	  }
	  if (angleOffset < -75) {
		  return 1;
	  }
	  if (angleOffset < -45) {
		  return 0.75;
	  }
	  if (angleOffset < -20) {
		  return 0.4;
	  }
	  if (angleOffset < -4) {
		  return 0.2;
	  }
	  return 0;
  }

  public void configClosedLoop(double p, double i, double d) {
    leftMaster.config_kP(0, p, 0);
    leftMaster.config_kI(0, i, 0);
    leftMaster.config_kD(0, d, 0);

    rightMaster.config_kP(0, p, 0);
    rightMaster.config_kI(0, i, 0);
    rightMaster.config_kD(0, d, 0);

  }

  public void endClosedLoop() {
    // rightMaster.setInverted(false);
    // rightSlave.setInverted(false);
  }

  public void PIDDrive(double dist) {

    leftMaster.setSelectedSensorPosition(0, 0, 0);
    rightMaster.setSelectedSensorPosition(0, 0, 0);
 //   leftMaster.configAllowableClosedloopError(0, 5, 0);
 //   rightMaster.configAllowableClosedloopError(0, 5, 0);
    leftMaster.set(ControlMode.Position,
        /*leftMaster.getSelectedSensorPosition(0) / 1024.0 * RobotMap.WHEEL_CIRCUMFERENCE*/
            -dist * 1024.0 * 1.25 / RobotMap.WHEEL_CIRCUMFERENCE);
    rightMaster.set(ControlMode.Position,
        /*rightMaster.getSelectedSensorPosition(0) / 1024.0 * RobotMap.WHEEL_CIRCUMFERENCE
            +*/ dist * 1024.0 * 1.25 / RobotMap.WHEEL_CIRCUMFERENCE);
    // leftSlave.set(ControlMode.Position, leftMaster.getSelectedSensorPosition(0) / 1024.0 *
    // RobotMap.WHEEL_CIRCUMFERENCE + dist * 1024.0 / RobotMap.WHEEL_CIRCUMFERENCE);
    // rightSlave.set(ControlMode.Position, leftMaster.getSelectedSensorPosition(0) / 1024.0 *
    // RobotMap.WHEEL_CIRCUMFERENCE + dist * 1024.0 / RobotMap.WHEEL_CIRCUMFERENCE);

    System.out.println("Destination: " + -dist * 1024.0 / RobotMap.WHEEL_CIRCUMFERENCE);

  }

  public void logInversion() {
    SmartDashboard.putBoolean("Left Master Inversion", leftMaster.getInverted());
    SmartDashboard.putBoolean("Right Master Inversion", rightMaster.getInverted());
    SmartDashboard.putBoolean("Left Slave Inversion", leftSlave.getInverted());
    SmartDashboard.putBoolean("Right Slave Inversion", rightSlave.getInverted());
   // SmartDashboard.putData("DifferentialDrive", robotDrive);

  }
  
  //-1 to 1 input
  public void autoTurn(double speed) {
    rightMaster.set(ControlMode.PercentOutput, speed);
    rightSlave.set(ControlMode.PercentOutput, speed);
    //rightSlave.set(ControlMode.PercentOutput, speed);
    leftMaster.set(ControlMode.PercentOutput, speed);
    //leftSlave.set(ControlMode.PercentOutput, speed);
    leftSlave.set(ControlMode.PercentOutput, speed);
  }

  public double getClosedLoopError(DriveSide side) {
    if (side == DriveSide.LEFT) {
      return leftMaster.getClosedLoopError(0) / 1024.0 * RobotMap.WHEEL_CIRCUMFERENCE;
    } else {
      return rightMaster.getClosedLoopError(0) / 1024.0 * RobotMap.WHEEL_CIRCUMFERENCE;

    }
  }

  /**
   * Sets initial yaw based on where our starting position is.
   */
  public void calibrateYaw() {
	/*
	 * This was for only for the 2017 game, with a non-symmetrical field
	 * 
    if (DriverStation.getInstance().getAlliance() == DriverStation.Alliance.Blue) {
      rotationOffset = -90.0;
    } else {
      rotationOffset = 90.0;
    } */
  }

  public void zeroYaw() {
    navx.zeroYaw();
  }

  /**
   * Updates the dead reckoning for our current position.
   */
  public void updatePosition(double navXYaw) {
    rotation = temporaryFixDegrees(navXYaw + rotationOffset);

    double encoderLeft = getEncoderDistance(DriveSide.LEFT);
    double encoderRight = getEncoderDistance(DriveSide.RIGHT);

    double deltaEncoderLeft = encoderLeft - prevEncoderLeft;
    double deltaEncoderRight = encoderRight - prevEncoderRight;

    double deltaInches = (deltaEncoderLeft + deltaEncoderRight) / 2;

    absoluteDistanceTraveled += Math.abs(deltaInches);

    positionX = positionX + Math.cos(Math.toRadians(90 - rotation)) * deltaInches;
    positionY = positionY + Math.sin(Math.toRadians(90 - rotation)) * deltaInches;

    prevEncoderLeft = encoderLeft;
    prevEncoderRight = encoderRight;
    
    SmartDashboard.putNumber("Yaw", rotation);
    
    SmartDashboard.putNumber("Left Encoder", encoderLeft);
    SmartDashboard.putNumber("Right Encoder", encoderRight);
    SmartDashboard.putNumber("Robot Direction", navXYaw);    
    
  }
  
  public void zeroPosition() {
	  positionX = 0;
	  positionY = 0;
	  zeroYaw();
	  zeroEncoders();
	  SmartDashboard.putNumber("Robot Direction", getRobotPosition().getDirection());
   // SmartDashboard.putNumber("Turn setPoint", turningController.getSetpoint());
  }
  
  public RobotPosition getRobotPosition() {
	  // TODO: I know Erik did this last year, but I don't like to "new" anything after initialization
	  // if we can help it. We should have a robotPosition attribute in this class and return it by
	  // value here.
	robotPosition.setRobotPosition(positionX, positionY, rotation);
    return robotPosition;
  }
  
  public double turnError() {
    return turningController.getError();
  }
  
  public double getSetpoint() {
    return turningController.getSetpoint();
  }

}


