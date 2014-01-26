/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2014  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 *  CPAchecker web page:
 *    http://cpachecker.sosy-lab.org
 */
package org.sosy_lab.cpachecker.appengine.dao;

import static org.junit.Assert.*;

import org.junit.Test;
import org.sosy_lab.cpachecker.appengine.common.DatabaseTest;
import org.sosy_lab.cpachecker.appengine.entity.Job;
import org.sosy_lab.cpachecker.appengine.entity.Job.Status;
import org.sosy_lab.cpachecker.appengine.entity.JobFile;
import org.sosy_lab.cpachecker.appengine.entity.JobStatistic;

import com.googlecode.objectify.Key;


public class JobDAOTest extends DatabaseTest {

  @Test
  public void shouldSaveJob() {
    Job job = new Job();
    JobDAO.save(job);

    assertTrue(job.getId() != null);
  }

  @Test
  public void shouldLoadJob() throws Exception {
    Job job = new Job();
    JobDAO.save(job);
    Job loaded = JobDAO.load(job.getKey());

    assertEquals(job, loaded);
  }

  @Test
  public void shouldDeleteJob() throws Exception {
    Job job = new Job(1L);
    job.setStatus(Status.DONE);

    JobFile file = new JobFile("", job);
    JobFileDAO.save(file);
    job.addFile(file);

    JobStatistic stats = new JobStatistic(job);
    JobStatisticDAO.save(stats);
    job.setStatistic(stats);

    JobDAO.save(job);

    Key<Job> jobKey = Key.create(job);
    Key<JobFile> fileKey = Key.create(file);
    Key<JobStatistic> statsKey = Key.create(stats);
    JobDAO.delete(job);

    assertNull(JobDAO.load(jobKey));
    assertNull(JobFileDAO.load(fileKey));
    assertNull(JobStatisticDAO.load(statsKey));
  }
}