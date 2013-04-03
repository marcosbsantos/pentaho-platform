package org.pentaho.platform.scheduler2.blockout;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.pentaho.platform.api.scheduler2.ComplexJobTrigger;
import org.pentaho.platform.api.scheduler2.CronJobTrigger;
import org.pentaho.platform.api.scheduler2.IJobTrigger;
import org.pentaho.platform.api.scheduler2.IScheduler;
import org.pentaho.platform.api.scheduler2.SchedulerException;
import org.pentaho.platform.api.scheduler2.SimpleJobTrigger;
import org.pentaho.platform.scheduler2.quartz.QuartzJobKey;
import org.pentaho.platform.scheduler2.quartz.QuartzScheduler;
import org.quartz.TriggerUtils;

public class BlockOutManagerUtil {

  /**
   * Standard Units of Time
   */
  public static enum TIME {
    MILLISECOND(1), SECOND(MILLISECOND.time * 1000), MINUTE(SECOND.time * 60), HOUR(MINUTE.time * 60), DAY(
        HOUR.time * 24), WEEK(DAY.time * 7), YEAR(DAY.time * 365);

    public long time;

    private TIME(long time) {
      this.time = time;
    }
  }

  public static boolean willFire(IJobTrigger jobTrigger, List<IJobTrigger> blockOutTriggers, IScheduler scheduler) {

    List<Date> fireTimes = getFireTimes(jobTrigger, scheduler);

    for (IJobTrigger blockOutJobTrigger : blockOutTriggers) {

      // We must verify further if the schedule is blocked completely or if it will fire
      if (willBlockSchedule(jobTrigger, blockOutJobTrigger, scheduler)) {

        // If recurrence intervals are the same, it will never fire
        if (!isComplexTrigger(blockOutJobTrigger) && !isComplexTrigger(jobTrigger)
            && getRecurrenceInterval(blockOutJobTrigger) == getRecurrenceInterval(jobTrigger)) {
          return false;
        }

        // Loop through fire times and verify whether block out is blocking the schedule completely
        boolean scheduleCompletelyBlocked = true;
        for (Date fireTime : fireTimes) {
          if (!(scheduleCompletelyBlocked = willBlockDate(blockOutJobTrigger, fireTime, scheduler))) {
            break;
          }
        }

        // Return false if after n iterations
        if (scheduleCompletelyBlocked) {
          return false;
        }
      }
    }

    return true;
  }

  public static boolean willBlockSchedule(IJobTrigger scheduleTrigger, IJobTrigger blockOutJobTrigger,
      IScheduler scheduler) {

    boolean isScheduleTriggerComplex = isComplexTrigger(scheduleTrigger);
    boolean isBlockOutTriggerComplex = isComplexTrigger(blockOutJobTrigger);

    // Both Schedule and BlockOut are complex
    if (isScheduleTriggerComplex && isBlockOutTriggerComplex) {
      return willComplexBlockOutBlockComplexScheduleTrigger(blockOutJobTrigger, scheduleTrigger, scheduler);
    }

    // Complex Schedule Trigger
    if (isScheduleTriggerComplex) {
      return willBlockComplexScheduleTrigger(scheduleTrigger, blockOutJobTrigger, scheduler);
    }

    // Complex BlockOut Trigger
    if (isBlockOutTriggerComplex) {
      return willComplexBlockOutTriggerBlockSchedule(blockOutJobTrigger, scheduleTrigger, scheduler);
    }

    /*
     * Both blockOut and schedule triggers are simple. Continue with mathematical calculations
     */
    long blockOutRecurrence = getRecurrenceInterval(blockOutJobTrigger);
    long scheduleRecurrence = getRecurrenceInterval(scheduleTrigger);

    for (int i = 0; i < 1000; i++) {
      double shiftBy = (blockOutRecurrence - scheduleRecurrence) * i / (double) scheduleRecurrence;

      double x1 = (blockOutJobTrigger.getStartTime().getTime() - scheduleTrigger.getStartTime().getTime())
          / (double) scheduleRecurrence + shiftBy;

      double x2 = (blockOutJobTrigger.getStartTime().getTime() + blockOutJobTrigger.getDuration() - scheduleTrigger
          .getStartTime().getTime()) / (double) scheduleRecurrence + shiftBy;

      if (hasIntBetween(x1, x2)) {

        int xShift = (int) Math.ceil(x1 < x2 ? x1 : x2);

        long scheduleDate = scheduleTrigger.getStartTime().getTime() + scheduleRecurrence * (i + xShift);
        long blockOutStartDate = blockOutJobTrigger.getStartTime().getTime() + blockOutRecurrence * i;

        // Test intersection of dates fall within range
        if (scheduleTrigger.getStartTime().getTime() <= scheduleDate
            && (scheduleTrigger.getEndTime() == null || scheduleDate <= scheduleTrigger.getEndTime().getTime())
            && blockOutJobTrigger.getStartTime().getTime() <= blockOutStartDate
            && (blockOutJobTrigger.getEndTime() == null || blockOutStartDate <= blockOutJobTrigger.getEndTime()
                .getTime())) {
          return true;
        }
      }
    }

    return false;
  }

  private static boolean willComplexBlockOutTriggerBlockSchedule(IJobTrigger blockOutJobTrigger,
      IJobTrigger scheduleTrigger, IScheduler scheduler) {

    // Short circuit if schedule trigger after end time of block out trigger
    if ((blockOutJobTrigger.getEndTime() != null && scheduleTrigger.getStartTime().after(
        blockOutJobTrigger.getEndTime()))
        || (scheduleTrigger.getEndTime() != null && blockOutJobTrigger.getStartTime().after(
            scheduleTrigger.getEndTime()))) {
      return false;
    }

    long duration = blockOutJobTrigger.getDuration();

    // Loop through fire times of block out trigger
    for (Date blockOutStartDate : getFireTimes(blockOutJobTrigger, scheduler)) {
      Date blockOutEndDate = new Date(blockOutStartDate.getTime() + duration);

      if (willBlockOutRangeBlockSimpleTrigger(blockOutStartDate, blockOutEndDate, scheduleTrigger, scheduler)) {
        return true;
      }
    }

    return false;
  }

  private static boolean willBlockOutRangeBlockSimpleTrigger(Date startBlockOutRange, Date endBlockOutRange,
      IJobTrigger scheduleTrigger, IScheduler scheduler) {
    // ( S1 - S ) / R <= x <= ( S2 - S ) / R 

    double recurrence = getRecurrenceInterval(scheduleTrigger);
    double x1 = (startBlockOutRange.getTime() - scheduleTrigger.getStartTime().getTime()) / recurrence;
    double x2 = (endBlockOutRange.getTime() - scheduleTrigger.getStartTime().getTime()) / recurrence;

    return hasPositiveIntBetween(x1, x2);
  }

  private static boolean willBlockComplexScheduleTrigger(IJobTrigger trigger, IJobTrigger blockOut, IScheduler scheduler) {

    for (Date fireTime : getFireTimes(trigger, scheduler)) {
      if (willBlockDate(blockOut, fireTime, scheduler)) {
        return true;
      }
    }

    return false;
  }

  private static boolean willComplexBlockOutBlockComplexScheduleTrigger(IJobTrigger blockOutJobTrigger,
      IJobTrigger jobTrigger, IScheduler scheduler) {
    List<Date> blockOutFireTimes = getFireTimes(blockOutJobTrigger, scheduler);

    int iStart = 0;
    for (Date scheduleFireTime : getFireTimes(jobTrigger, scheduler)) {
      for (int i = iStart; i < blockOutFireTimes.size(); i++) {
        Date blockOutStartDate = blockOutFireTimes.get(i);

        // BlockOut start date after scheduled fire time
        if (blockOutStartDate.after(scheduleFireTime)) {
          iStart = i;
          break;
        }

        Date blockOutEndDate = new Date(blockOutStartDate.getTime() + blockOutJobTrigger.getDuration());

        if (isDateIncludedInRangeInclusive(blockOutStartDate, blockOutEndDate, scheduleFireTime)) {
          return true;
        }
      }

    }

    return false;
  }

  private static boolean willBlockDate(IJobTrigger blockOutJobTrigger, Date date, IScheduler scheduler) {
    // S + Rx <= d <= S + Rx + D

    // Out of range of block out
    if (date.before(blockOutJobTrigger.getStartTime())
        || (blockOutJobTrigger.getEndTime() != null && date.after(blockOutJobTrigger.getEndTime()))) {
      return false;
    }

    if (isComplexTrigger(blockOutJobTrigger)) {
      return willComplexBlockOutTriggerBlockDate(blockOutJobTrigger, date, scheduler);
    }

    long blockOutRecurrenceInterval = getRecurrenceInterval(blockOutJobTrigger);

    double x1 = (date.getTime() - blockOutJobTrigger.getStartTime().getTime()) / (double) blockOutRecurrenceInterval;
    double x2 = (date.getTime() - (blockOutJobTrigger.getStartTime().getTime() + blockOutJobTrigger.getDuration()))
        / (double) blockOutRecurrenceInterval;

    return hasPositiveIntBetween(x1, x2);
  }

  private static boolean willComplexBlockOutTriggerBlockDate(IJobTrigger blockOutJobTrigger, Date date,
      IScheduler scheduler) {

    // Short circuit if date does not fall within a valid start/end date range
    if (date.before(blockOutJobTrigger.getStartTime())
        || (blockOutJobTrigger.getEndTime() != null && date.after(blockOutJobTrigger.getEndTime()))) {
      return false;
    }

    long blockOutDuration = blockOutJobTrigger.getDuration();
    for (Date blockOutStartDate : getFireTimes(blockOutJobTrigger, scheduler)) {

      // Block out date has passed the date being tested
      if (blockOutStartDate.after(date)) {
        break;
      }

      Date blockOutEndDate = new Date(blockOutStartDate.getTime() + blockOutDuration);

      // Date falls within inclusive block out range
      if (isDateIncludedInRangeInclusive(blockOutStartDate, blockOutEndDate, date)) {
        return true;
      }
    }

    return false;
  }

  public static boolean isComplexTrigger(IJobTrigger jobTrigger) {
    return jobTrigger instanceof ComplexJobTrigger || jobTrigger instanceof CronJobTrigger;
  }

  private static long getRecurrenceInterval(IJobTrigger jobTrigger) {

    if (!isComplexTrigger(jobTrigger)) {
      return ((SimpleJobTrigger) jobTrigger).getRepeatInterval() * 1000; // Have to convert to milliseconds
    }

    throw new RuntimeException("Can not get recurrence interval from JobTriggers which are not SimpleJobTrigger"); //$NON-NLS-1$
  }

  @SuppressWarnings("unchecked")
  public static List<Date> getFireTimes(IJobTrigger jobTrigger, IScheduler scheduler) {

    // Determines the maximum amount of fire times allowed to be calculated
    int n = 1000;

    Date startDate = jobTrigger.getStartTime();
    Date endDate = new Date(startDate.getTime() + 4 * TIME.YEAR.time);

    // Quartz Triggers
    if (QuartzScheduler.class.isAssignableFrom(scheduler.getClass())) {
      try {

        List<Date> dates = new ArrayList<Date>();
        long interval = TIME.WEEK.time;
        Date intervalEndDate = new Date(startDate.getTime() + interval);

        while (dates.size() < n && startDate.before(endDate)) {

          dates.addAll(TriggerUtils.computeFireTimesBetween(
              QuartzScheduler.createQuartzTrigger(jobTrigger, new QuartzJobKey("test", "test")), //$NON-NLS-1$ //$NON-NLS-2$
              null, startDate, intervalEndDate));

          startDate = intervalEndDate;
          intervalEndDate = new Date(startDate.getTime() + interval);
        }

        return dates;
      } catch (SchedulerException e) {
        throw new RuntimeException(e);
      }
    }

    throw new RuntimeException("Can not calculate fire times for unsupported Scheduler Type: " //$NON-NLS-1$
        + scheduler.getClass().getSimpleName());
  }

  //  public static Trigger convertToQuartzTrigger(IJobTrigger jobTrigger) {
  //    try {
  //      Trigger quartzTrigger = null;
  //
  //      if (jobTrigger instanceof SimpleJobTrigger) {
  //        SimpleJobTrigger simpleJobTrigger = (SimpleJobTrigger) jobTrigger;
  //
  //        quartzTrigger = new SimpleTrigger("test", simpleJobTrigger.getStartTime(), simpleJobTrigger.getEndTime(), //$NON-NLS-1$
  //            simpleJobTrigger.getRepeatCount(), simpleJobTrigger.getRepeatInterval());
  //
  //      } else if (jobTrigger instanceof CronJobTrigger) {
  //        CronJobTrigger cronJobTrigger = (CronJobTrigger) jobTrigger;
  //
  //        quartzTrigger = new CronTrigger("test", "test", "test", "test", cronJobTrigger.getStartTime(), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
  //            cronJobTrigger.getEndTime(), cronJobTrigger.getCronString());
  //
  //      } else if (jobTrigger instanceof ComplexJobTrigger) {
  //        ComplexJobTrigger complexJobTrigger = (ComplexJobTrigger) jobTrigger;
  //
  //        if (complexJobTrigger.getCronString() != null) {
  //          quartzTrigger = new CronTrigger("test", "test", "test", "test", complexJobTrigger.getStartTime(), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
  //              complexJobTrigger.getEndTime(), complexJobTrigger.getCronString());
  //        } else {
  //          quartzTrigger = new CronTrigger("test", "test", "test", "test", complexJobTrigger.getStartTime(), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
  //              complexJobTrigger.getEndTime(), QuartzCronStringFactory.createCronString(complexJobTrigger));
  //        }
  //      }
  //
  //      return quartzTrigger;
  //
  //    } catch (ParseException e) {
  //      throw new RuntimeException(e);
  //    }
  //  }

  public static boolean shouldFireNow(List<IJobTrigger> blockOutJobTriggers, IScheduler scheduler) {

    Date currentTime = new Date(System.currentTimeMillis());
    for (IJobTrigger blockOutJobTrigger : blockOutJobTriggers) {

      if (willBlockDate(blockOutJobTrigger, currentTime, scheduler)) {
        return false;
      }
    }

    return true;
  }

  public static boolean isPartiallyBlocked(IJobTrigger scheduleJobTrigger, List<IJobTrigger> blockOutJobTriggers,
      IScheduler scheduler) {

    // Loop through blockout triggers
    for (IJobTrigger blockOut : blockOutJobTriggers) {
      if (willBlockSchedule(scheduleJobTrigger, blockOut, scheduler)) {
        return true;
      }
    }

    return false;
  }

  /**
   * @param x1 double
   * @param x2 double
   * @return whether an {@link Integer} exists between x1 and x2
   */
  private static boolean hasIntBetween(double x1, double x2) {
    double ceilX = Math.ceil(x1);
    double floorX = Math.floor(x2);

    if (x1 > x2) {
      ceilX = Math.ceil(x2);
      floorX = Math.floor(x1);
    }

    return (floorX - ceilX) >= 0;
  }

  /**
   * @param x1 double
   * @param x2 double
   * @return whether there is a positive integer between x1 and x2 
   */
  private static boolean hasPositiveIntBetween(double x1, double x2) {
    return (x1 < x2 ? x2 >= 0 : x1 >= 0) && hasIntBetween(x1, x2);
  }

  /**
   * @param dateRangeStart
   *        {@link Date} start of range
   * @param dateRangeEnd
   *        {@link Date} end of range
   * @param date
   *        {@link Date}
   * @return whether the date falls within the date inclusive date range
   */
  private static boolean isDateIncludedInRangeInclusive(Date dateRangeStart, Date dateRangeEnd, Date date) {
    long dateTime = date.getTime();
    return dateRangeStart.getTime() <= dateTime && dateTime <= dateRangeEnd.getTime();
  }

}
