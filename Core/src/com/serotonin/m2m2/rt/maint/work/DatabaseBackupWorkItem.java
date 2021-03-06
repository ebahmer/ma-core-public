package com.serotonin.m2m2.rt.maint.work;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.comparator.LastModifiedFileComparator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.joda.time.DateTime;

import com.serotonin.ShouldNeverHappenException;
import com.serotonin.m2m2.Common;
import com.serotonin.m2m2.db.dao.SystemSettingsDao;
import com.serotonin.m2m2.i18n.ProcessResult;
import com.serotonin.m2m2.i18n.TranslatableMessage;
import com.serotonin.m2m2.rt.event.type.SystemEventType;
import com.serotonin.m2m2.util.DateUtils;
import com.serotonin.timer.CronTimerTrigger;
import com.serotonin.timer.RejectedTaskReason;
import com.serotonin.timer.TimerTask;
import com.serotonin.util.StringUtils;

public class DatabaseBackupWorkItem implements WorkItem {
	private static final Log LOG = LogFactory.getLog(DatabaseBackupWorkItem.class);
	// Used for filename and property value for last run
	public static final String BACKUP_DATE_FORMAT = "MMM-dd-yyyy_HHmmss"; 
	public static final SimpleDateFormat dateFormatter = new SimpleDateFormat(BACKUP_DATE_FORMAT);

	// Lock to ensure we don't clobber files by running a backup
	// during another one.
	public static final Object lock = new Object();

	private static final String TASK_ID = "DatabaseBackupTask";
	
	private static DatabaseBackupTask task; // Static holder to re-schedule task
											// if necessary
	private String backupLocation; // Location of backup directory on disk
    private volatile boolean finished = false;
    private volatile boolean cancelled = false;
    private volatile boolean failed = false;
    private String filename;
    
	/**
	 * Statically Schedule a Timer Task that will run this work item.
	 * 
	 * TODO For Startup Make this an RTM and add to the RuntimeManager's list of
	 * startup RTMS currently only the Module RTMs get started there. For now
	 * this call is used within the RuntimeManager
	 */
	public static void schedule() {
		try {
			// Test trigger for running every 25 seconds.
			// String cronTrigger = "0/25 * * * * ?";

			// Trigger to run at Set Hour/Minute "s m h * * ?";
			int hour = SystemSettingsDao.getIntValue(SystemSettingsDao.DATABASE_BACKUP_HOUR);
			int minute = SystemSettingsDao.getIntValue(SystemSettingsDao.DATABASE_BACKUP_MINUTE);

			String cronTrigger = "0 " + minute + " " + hour + " * * ?";
			task = new DatabaseBackupTask(cronTrigger);
			Common.timer.schedule(task);
		} catch (ParseException e) {
			throw new ShouldNeverHappenException(e);
		}
	}

	/**
	 * Safely unschedule the timer task for this work item
	 */
	public static void unschedule() {
		if (task != null) {
			task.cancel();
		}
	}

	@Override
	public int getPriority() {
		return WorkItem.PRIORITY_MEDIUM;
	}

	/**
	 * Queue a backup for execution
	 * 
	 * @param backupLocation
	 */
	public static void queueBackup(String backupLocation) {
		DatabaseBackupWorkItem item = new DatabaseBackupWorkItem();
		item.backupLocation = backupLocation;
		Common.backgroundProcessing.addWorkItem(item);
	}

	@Override
	public void execute() {
		synchronized (lock) {
			LOG.info("Starting database backup WorkItem.");

			// Create the filename
			String filename = "core-database-" + Common.databaseProxy.getType();
			String runtimeString = dateFormatter.format(new Date());
			int maxFiles = SystemSettingsDao.getIntValue(SystemSettingsDao.DATABASE_BACKUP_FILE_COUNT);
			// If > 1 then we will use a date in the filename
			if (maxFiles > 1) {
				// Create Mango-Configuration-date.json
				filename += "-";
				filename += runtimeString;
			}

			// Fill the full path
			String fullFilePath = this.backupLocation;
			if (fullFilePath.endsWith(File.separator)) {
				fullFilePath += filename;
			} else {
				fullFilePath += File.separator;
				fullFilePath += filename;
			}

			if(cancelled)
				return;
			
			// Execute the Backup
			try {

				switch (Common.databaseProxy.getType()) {
				case H2:
					String[] backupScript = new String[] { "SCRIPT DROP TO '" + fullFilePath + ".zip' COMPRESSION ZIP;" };
					OutputStream out = createLogOutputStream();
					Common.databaseProxy.runScript(backupScript, out);
					break;
				case MYSQL:
					String dumpExePath = Common.envProps.getString("db.mysqldump", "mysqldump");
					// Of the form: jdbc:mysql://localhost/mango2712
					// or: jdbc:mysql://localhost:3306/sakila?profileSQL=true
					String cnxn = Common.envProps.getString("db.url");
					String[] parts = cnxn.split("/");
					String[] hostPort = parts[2].split(":");
					String host = hostPort[0];
					String port;
					if (hostPort.length > 1)
						port = hostPort[1];
					else
						port = "3306";
					String user = Common.envProps.getString("db.username");
					String password = Common.databaseProxy.getDatabasePassword("");
					// Split off any extra stuff on the db
					String[] dbParts = parts[3].split("\\?");
					String database = dbParts[0];
					backupMysqlWithOutDatabase(dumpExePath, host, port, user, password, database, fullFilePath);
					break;
				case DERBY:
				case MSSQL:
				case POSTGRES:
				default:
					LOG.warn(
							"Unable to backup database, because no script for type: " + Common.databaseProxy.getType());
					return;

				}

				File file = new File(fullFilePath + ".zip");
				if (!file.exists()) {
					failed = true;
					LOG.warn("Unable to create backup file: " + fullFilePath);
					backupFailed(fullFilePath, "Unable to create backup file");
					return;
				}
				
				//Save the filename
				this.filename = file.getAbsolutePath();
				
				// Store the last successful backup time
				SystemSettingsDao.instance.setValue(SystemSettingsDao.DATABASE_BACKUP_LAST_RUN_SUCCESS, runtimeString);

				// Clean up old files, keeping the correct number as the history
				File[] files = getBackupFiles(this.backupLocation);

				// Sort the files by date
				Arrays.sort(files, LastModifiedFileComparator.LASTMODIFIED_REVERSE);

				// Keep the desired history
				for (int i = maxFiles; i < files.length; i++) {
					try {
						files[i].delete(); // Remove it
					} catch (Exception e) {
						LOG.warn("Unable to delete file: " + files[i].getAbsolutePath(), e);
					}
				}

			} catch (Exception e) {
				LOG.warn(e);
				failed = true;
				backupFailed(fullFilePath, e.getMessage());
			}finally{
				finished = true;
			}
		}
	}
	
	private void backupFailed(String fullFilePath, String message) {
		SystemEventType.raiseEvent(new SystemEventType(SystemEventType.TYPE_BACKUP_FAILURE),
				Common.timer.currentTimeMillis(), false, new TranslatableMessage("event.backup.failure",
						fullFilePath, message));
	}

	protected OutputStream createLogOutputStream() {
		String dir = Common.envProps.getString("db.update.log.dir", "");
		dir = StringUtils.replaceMacros(dir, System.getProperties());

		File logDir = new File(dir);
		File logFile = new File(logDir, getClass().getName() + ".log");
		LOG.info("Writing upgrade log to " + logFile.getAbsolutePath());

		try {
			if (logDir.isDirectory() && logDir.canWrite())
				return new FileOutputStream(logFile);
		} catch (Exception e) {
			LOG.error("Failed to create database backup log file.", e);
		}

		LOG.warn("Failing over to console for printing database backup messages");
		return System.out;
	}

	/**
	 * Timer task that uses this Backup Work Item in its execution
	 * 
	 * @author tpacker
	 *
	 */
	static class DatabaseBackupTask extends TimerTask {
		DatabaseBackupTask(String cronTrigger) throws ParseException {
			super(new CronTimerTrigger(cronTrigger), "Database backup");
		}

		@Override
		public void run(long runtime) {

			// Should we run the backup?
			try {
				String lastRunDateString = SystemSettingsDao
						.getValue(SystemSettingsDao.DATABASE_BACKUP_LAST_RUN_SUCCESS);
				String backupLocation = SystemSettingsDao.getValue(SystemSettingsDao.DATABASE_BACKUP_FILE_LOCATION);

				// Have we ever run?
				if (lastRunDateString != null) {
					Date lastRunDate = dateFormatter.parse(lastRunDateString);
					DateTime lastRun = new DateTime(lastRunDate);
					// Compute the next run time off of the last run time
					DateTime nextRun = DateUtils.plus(lastRun,
							SystemSettingsDao.getIntValue(SystemSettingsDao.DATABASE_BACKUP_PERIOD_TYPE),
							SystemSettingsDao.getIntValue(SystemSettingsDao.DATABASE_BACKUP_PERIODS));
					// Is the next run time now or before now?
					if (!nextRun.isAfter(runtime)) {
						DatabaseBackupWorkItem.queueBackup(backupLocation);
					}

				} else {
					DatabaseBackupWorkItem.queueBackup(backupLocation);
				}
			} catch (Exception e) {
				LOG.error(e);
				SystemEventType.raiseEvent(new SystemEventType(SystemEventType.TYPE_BACKUP_FAILURE),
						Common.timer.currentTimeMillis(), false,
						new TranslatableMessage("event.backup.failure", "no file", e.getMessage()));

			}
		}// end run
	}// end backup settings task

	/**
	 * @return
	 */
	public static File[] getBackupFiles(String backupLocation) {
		File backupDir = new File(backupLocation);
		String nameStart = "core-database-" + Common.databaseProxy.getType();
		final String lowerNameStart = nameStart.toLowerCase();
		File[] files = backupDir.listFiles(new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return name.toLowerCase().startsWith(lowerNameStart);
			}
		});
		return files;
	}

	/**
	 * Restore a database from a backup
	 * 
	 * @param file
	 */
	public static ProcessResult restore(String filename) {
		ProcessResult result = new ProcessResult();

		// Fill the full path
		String fullFilePath = SystemSettingsDao.getValue(SystemSettingsDao.DATABASE_BACKUP_FILE_LOCATION);

		if (fullFilePath.endsWith(File.separator)) {
			fullFilePath += filename;
		} else {
			fullFilePath += File.separator;
			fullFilePath += filename;
		}

		File file = new File(fullFilePath);
		if (file.exists() && file.canRead()) {

			try {

				switch (Common.databaseProxy.getType()) {
				case H2:
					String[] backupScript = new String[] { "RUNSCRIPT FROM '" + fullFilePath + "' COMPRESSION ZIP;" };
					// TODO Create a stream to print to the result
					Common.databaseProxy.runScript(backupScript, System.out);
					break;
				case MYSQL:
					String mySqlPath = Common.envProps.getString("db.mysql", "mysql");
					// Of the form: jdbc:mysql://localhost/mango2712
					// or: jdbc:mysql://localhost:3306/sakila?profileSQL=true
					String cnxn = Common.envProps.getString("db.url");
					String[] parts = cnxn.split("/");
					String[] hostPort = parts[2].split(":");
					String host = hostPort[0];
					String port;
					if (hostPort.length > 1)
						port = hostPort[1];
					else
						port = "3306";
					String user = Common.envProps.getString("db.username");
					String password = Common.databaseProxy.getDatabasePassword("");
					// Split off any extra stuff on the db
					String[] dbParts = parts[3].split("\\?");
					String database = dbParts[0];
					result.addMessage(restoreMysqlToDatabase(mySqlPath, host, port, user, password, database, fullFilePath));
					break;
				case DERBY:
				case MSSQL:

				case POSTGRES:
				default:
					LOG.warn(
							"Unable to restore database, because no script for type: " + Common.databaseProxy.getType());
					result.addMessage(new TranslatableMessage("systemSettings.databaseRestoreNotSupported",
							Common.databaseProxy.getType()));
					return result;
				}
			} catch (Exception e) {
				LOG.error(e.getMessage(), e);
				result.addMessage(new TranslatableMessage("common.default", e.getMessage()));
			}

		}

		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.serotonin.m2m2.rt.maint.work.WorkItem#getDescription()
	 */
	@Override
	public String getDescription() {
		return "Backing up database to " + this.backupLocation;
	}

	/**
	 * Backup mysql database without the database name implied, easier to import
	 * into various systems
	 * 
	 * @param dumpExePath
	 * @param host
	 * @param port
	 * @param user
	 * @param password
	 * @param database
	 * @param backupPath
	 * @return
	 */
	public void backupMysqlWithOutDatabase(String dumpExePath, String host, String port, String user,
			String password, String database, String backupPath) {
		try {
			Process p = null;

			// only backup the data not included create database
			List<String> args = new ArrayList<String>();
			args.add(dumpExePath);
			args.add("-h" + host);
			args.add("-P" + port);
			args.add("-u" + user);
			args.add("-p" + password);
			args.add(database);

			ProcessBuilder pb = new ProcessBuilder(args);
			pb.redirectError(Redirect.INHERIT);
			File rawOutputFile = new File(backupPath + ".sql");
			pb.redirectOutput(Redirect.to(rawOutputFile));

			p = pb.start();

			int processComplete = p.waitFor();

			if (processComplete == 0) {
				byte[] buffer = new byte[1024];

				try {

					FileOutputStream fos = new FileOutputStream(backupPath + ".zip");
					ZipOutputStream zos = new ZipOutputStream(fos);
					ZipEntry ze = new ZipEntry(rawOutputFile.getName());
					zos.putNextEntry(ze);
					FileInputStream in = new FileInputStream(rawOutputFile);

					int len;
					while ((len = in.read(buffer)) > 0) {
						zos.write(buffer, 0, len);
					}

					in.close();
					zos.closeEntry();

					zos.close();
				} catch (IOException ex) {
					ex.printStackTrace();
				}finally{
					// Delete the sql file
					if(rawOutputFile.exists())
						rawOutputFile.delete();
				}

				LOG.info("Backup created successfully for" + database + " in " + host + ":" + port);
			} else {
				LOG.info("Could not create the backup for " + database + " in " + host + ":" + port);
				backupFailed(backupPath, "Process exit status: "+processComplete);
			}

		} catch (IOException ioe) {
			LOG.error(ioe, ioe.getCause());
			backupFailed(backupPath, ioe.getMessage());
		} catch (Exception e) {
			LOG.error(e, e.getCause());
			backupFailed(backupPath, e.getMessage());
		}
	}

	public static TranslatableMessage restoreMysqlToDatabase(String mysqlPath, String host, String port, String user, String password,
			String database, String backupZipFile) {
		TranslatableMessage status = null;
		File sqlFile = new File(backupZipFile.replace(".zip", ".sql"));
		try {
			Process p = null;

			// only backup the data not included create database
			List<String> args = new ArrayList<String>();
			args.add(mysqlPath);
			args.add("-h" + host);
			args.add("-P" + port);
			args.add("-u" + user);
			args.add("-p" + password);
			args.add(database);

			// Unzip the File
			byte[] buffer = new byte[1024];
			try {
				// get the zip file content
				ZipInputStream zis = new ZipInputStream(new FileInputStream(backupZipFile));
				// get the zipped file list entry
				ZipEntry ze = zis.getNextEntry();

				if (ze != null) {					
					FileOutputStream fos = new FileOutputStream(sqlFile);

					int len;
					while ((len = zis.read(buffer)) > 0) {
						fos.write(buffer, 0, len);
					}

					fos.close();
					ze = zis.getNextEntry();
				}

				zis.closeEntry();
				zis.close();
			} catch (IOException ex) {
				LOG.error(ex, ex.getCause());
				return new TranslatableMessage("systemSettings.databaseRestoreFailed", ex.getMessage());
			}

			ProcessBuilder pb = new ProcessBuilder(args);
			pb.redirectError(Redirect.INHERIT);
			pb.redirectInput(Redirect.from(sqlFile));

			p = pb.start();

			int processComplete = p.waitFor();

			if (processComplete == 0) {
				status = new TranslatableMessage("systemSettings.databaseRestored");
				LOG.info("Backup restored successfully for" + database + " in " + host + ":" + port);
			} else {
				status = new TranslatableMessage("systemSettings.databaseRestoreFailed", processComplete);
				LOG.info("Could not restore the backup for " + database + " in " + host + ":" + port);
			}

		} catch (IOException ioe) {
			LOG.error(ioe, ioe.getCause());
			status = new TranslatableMessage("systemSettings.databaseRestoreFailed", ioe.getMessage());
		} catch (Exception e) {
			LOG.error(e, e.getCause());
			new TranslatableMessage("systemSettings.databaseRestoreFailed", e.getMessage());
		}finally{
			if(sqlFile.exists())
				sqlFile.delete();
		}
		return status;
	}
	/* (non-Javadoc)
	 * @see com.serotonin.m2m2.rt.maint.work.WorkItem#getTaskId()
	 */
	@Override
	public String getTaskId() {
		return TASK_ID;
	}

	/* (non-Javadoc)
	 * @see com.serotonin.m2m2.util.timeout.TimeoutClient#getQueueSize()
	 */
	@Override
	public int getQueueSize() {
		return Common.defaultTaskQueueSize;
	}

	/* (non-Javadoc)
	 * @see com.serotonin.m2m2.rt.maint.work.WorkItem#rejected(com.serotonin.timer.RejectedTaskReason)
	 */
	@Override
	public void rejected(RejectedTaskReason reason) {
		//No Special handling
	}

	/**
	 * @param backupLocation2
	 */
	public void setBackupLocation(String location) {
		this.backupLocation = location;
	}

	/**
	 * @return
	 */
	public boolean isFinished() {
		return this.finished;
	}

	public boolean isFailed(){
		return this.failed;
	}
	
	/**
	 * 
	 */
	public void cancel() {
		this.cancelled = true;
	}
	
	public String getFilename(){
		return this.filename;
	}
}
