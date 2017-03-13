--
--    Copyright (C) 2014 Infinite Automation Systems Inc. All rights reserved.
--    @author Matthew Lohbihler
--
--
-- System settings
create table systemSettings (
  settingName varchar(64) not null,
  settingValue clob
);
alter table systemSettings add constraint systemSettingsPk primary key (settingName);

--
-- Templates
CREATE TABLE templates (
  id int not null generated by default as identity (start with 1, increment by 1),
  xid varchar(50) NOT NULL,
  name varchar(255),
  templateType varchar(50),
  readPermission varchar(255),
  setPermission varchar(255),
  data longblob NOT NULL
);
ALTER TABLE templates ADD CONSTRAINT templatesPk primary key (id);
ALTER TABLE templates ADD CONSTRAINT templatesUn1 UNIQUE (xid);

--
-- Users
create table users (
  id int not null generated by default as identity (start with 1, increment by 1),
  username varchar(40) not null,
  password varchar(255) not null,
  email varchar(255) not null,
  phone varchar(40),
  disabled char(1) not null,
  lastLogin bigint,
  homeUrl varchar(255),
  receiveAlarmEmails int not null,
  receiveOwnAuditEvents char(1) not null,
  timezone varchar(50),
  muted char(1),
  permissions varchar(255),
  name varchar(255),
  locale varchar(50)
);
alter table users add constraint usersPk primary key (id);

create table userComments (
  id int not null generated by default as identity (start with 1, increment by 1),
  xid varchar(50) NOT NULL,
  userId int,
  commentType int not null,
  typeKey int not null,
  ts bigint not null,
  commentText varchar(1024) not null
);
alter table userComments add constraint userCommentsFk1 foreign key (userId) references users(id);
alter table userComments add constraint userCommentsPk primary key (id);
ALTER TABLE userComments ADD CONSTRAINT userCommentsUn1 UNIQUE (xid);

--
-- Mailing lists
create table mailingLists (
  id int not null generated by default as identity (start with 1, increment by 1),
  xid varchar(50) not null,
  name varchar(40) not null
);
alter table mailingLists add constraint mailingListsPk primary key (id);
alter table mailingLists add constraint mailingListsUn1 unique (xid);

create table mailingListInactive (
  mailingListId int not null,
  inactiveInterval int not null
);
alter table mailingListInactive add constraint mailingListInactiveFk1 foreign key (mailingListId) 
  references mailingLists(id) on delete cascade;

create table mailingListMembers (
  mailingListId int not null,
  typeId int not null,
  userId int,
  address varchar(255)
);
alter table mailingListMembers add constraint mailingListMembersFk1 foreign key (mailingListId) 
  references mailingLists(id) on delete cascade;




--
--
-- Data Sources
--
create table dataSources (
  id int not null generated by default as identity (start with 1, increment by 1),
  xid varchar(50) not null,
  name varchar(40) not null,
  dataSourceType varchar(40) not null,
  data blob not null,
  rtdata blob,
  editPermission varchar(255)
);
alter table dataSources add constraint dataSourcesPk primary key (id);
alter table dataSources add constraint dataSourcesUn1 unique (xid);
ALTER TABLE dataSources ADD INDEX nameIndex (name ASC);

--
--
-- Data Points
--
create table dataPoints (
  id int not null generated by default as identity (start with 1, increment by 1),
  xid varchar(50) not null,
  dataSourceId int not null,
  name varchar(255),
  deviceName varchar(255),
  enabled char(1),
  pointFolderId int,
  loggingType int,
  intervalLoggingPeriodType int,
  intervalLoggingPeriod int,
  intervalLoggingType int,
  tolerance double,
  purgeOverride char(1),
  purgeType int,
  purgePeriod int,
  defaultCacheSize int,
  discardExtremeValues char(1),
  engineeringUnits int,
  data blob not null,
  readPermission varchar(255),
  setPermission varchar(255),
  templateId int,
  rollup int
);
alter table dataPoints add constraint dataPointsPk primary key (id);
alter table dataPoints add constraint dataPointsUn1 unique (xid);
alter table dataPoints add constraint dataPointsFk1 foreign key (dataSourceId) references dataSources(id);
ALTER TABLE dataPoints ADD CONSTRAINT dataPointsFk2 FOREIGN KEY (templateId) REFERENCES templates(id);
CREATE INDEX pointNameIndex on dataPoints (name ASC);
CREATE INDEX deviceNameIndex on dataPoints (deviceName ASC);
CREATE INDEX pointFolderIdIndex on dataPoints (pointFolderId ASC);
CREATE INDEX deviceNameNameIndex on dataPoints (deviceName ASC, name ASC);
CREATE INDEX enabledIndex on dataPoints (enabled ASC);
CREATE INDEX xidNameIndex on dataPoints (xid ASC, name ASC);


-- Data point hierarchy
CREATE TABLE dataPointHierarchy (
  id int NOT NULL,
  parentId int,
  name varchar(100)
);
ALTER TABLE dataPointHierarchy ADD CONSTRAINT dataPointHierarchyPk PRIMARY KEY (id);


--
--
-- Point Values (historical data)
--
create table pointValues (
  id bigint not null generated by default as identity (start with 1, increment by 1),
  dataPointId int not null,
  dataType int not null,
  pointValue double,
  ts bigint not null
);
alter table pointValues add constraint pointValuesPk primary key (id);
create index pointValuesIdx1 on pointValues (dataPointId, ts);

create table pointValueAnnotations (
  pointValueId bigint not null,
  textPointValueShort varchar(128),
  textPointValueLong clob,
  sourceMessage clob
);
alter table pointValueAnnotations add constraint pointValueAnnotationsPk primary key (pointValueId);

--
--
-- Event detectors
--
CREATE TABLE eventDetectors (
  id int NOT NULL generated by default as identity (start with 1, increment by 1),
  xid varchar(50) NOT NULL,
  sourceTypeName varchar(32) NOT NULL,
  typeName varchar(32) NOT NULL,
  dataPointId int,
  data longtext NOT NULL,
  PRIMARY KEY (id)
);
ALTER TABLE eventDetectors ADD CONSTRAINT eventDetectorsUn1 UNIQUE (xid, dataPointId);
ALTER TABLE eventDetectors ADD CONSTRAINT dataPointIdFk FOREIGN KEY (dataPointId) REFERENCES dataPoints(id);
--
--
-- Events
--
create table events (
  id int not null generated by default as identity (start with 1, increment by 1),
  typeName varchar(32) not null,
  subtypeName varchar(32),
  typeRef1 int not null,
  typeRef2 int not null,
  activeTs bigint not null,
  rtnApplicable char(1) not null,
  rtnTs bigint,
  rtnCause int,
  alarmLevel int not null,
  message clob,
  ackTs bigint,
  ackUserId int,
  alternateAckSource clob
);
alter table events add constraint eventsPk primary key (id);
alter table events add constraint eventsFk1 foreign key (ackUserId) references users(id);

create table userEvents (
  eventId int not null,
  userId int not null,
  silenced char(1) not null
);
alter table userEvents add constraint userEventsPk primary key (eventId, userId);
alter table userEvents add constraint userEventsFk1 foreign key (eventId) references events(id) on delete cascade;
alter table userEvents add constraint userEventsFk2 foreign key (userId) references users(id);


--
--
-- Event handlers
--
create table eventHandlers (
  id int not null generated by default as identity (start with 1, increment by 1),
  xid varchar(50) not null,
  alias varchar(255),
  eventHandlerType varchar(40) NOT NULL,
  
  -- Event type, see events
  eventTypeName varchar(32) not null,
  eventSubtypeName varchar(32),
  eventTypeRef1 int not null,
  eventTypeRef2 int not null,
  
  data blob not null
);
alter table eventHandlers add constraint eventHandlersPk primary key (id);
alter table eventHandlers add constraint eventHandlersUn1 unique (xid);

--
--
-- Audit Table
-- 
CREATE TABLE audit (
  id int not null generated by default as identity (start with 1, increment by 1),
  typeName varchar(32) NOT NULL,
  alarmLevel int NOT NULL,
  userId int NOT NULL,
  changeType int NOT NULL,
  objectId int NOT NULL,
  ts bigint NOT NULL,
  context longtext,
  message varchar(255),
);
alter table audit add constraint auditPk primary key (id);
CREATE INDEX tsIndex ON audit (ts ASC);
CREATE INDEX userIdIndex ON audit (userId ASC);
CREATE INDEX typeNameIndex ON audit (typeName ASC);
CREATE INDEX alarmLevelIndex ON audit (alarmLevel ASC);
--
--
-- Publishers
--
create table publishers (
  id int not null generated by default as identity (start with 1, increment by 1),
  xid varchar(50) not null,
  publisherType varchar(40) not null,
  data blob not null,
  rtdata blob
);
alter table publishers add constraint publishersPk primary key (id);
alter table publishers add constraint publishersUn1 unique (xid);

--
--
-- JsonData
--
CREATE TABLE jsonData (
  	id int not null generated by default as identity (start with 1, increment by 1),
	xid varchar(50) not null,
	name varchar(255) not null,
	readPermission varchar(255),
  	editPermission varchar(255),
  	publicData char(1),
  	data clob
);
ALTER TABLE jsonData ADD CONSTRAINT jsonDataUn1 UNIQUE (xid);

--
--
-- Compound events detectors
--
-- create table compoundEventDetectors (
--   id int not null generated by default as identity (start with 1, increment by 1),
--   xid varchar(50) not null,
--   name varchar(100),
--   alarmLevel int not null,
--   returnToNormal char(1) not null,
--   disabled char(1) not null,
--   conditionText varchar(256) not null
-- );
-- alter table compoundEventDetectors add constraint compoundEventDetectorsPk primary key (id);
-- alter table compoundEventDetectors add constraint compoundEventDetectorsUn1 unique (xid);

