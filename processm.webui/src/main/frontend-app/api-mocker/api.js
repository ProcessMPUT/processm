/* eslint-disable @typescript-eslint/no-var-requires */
const _ = require("lodash");
const delay = require("mocker-api/utils/delay");
const fs = require("fs");
const responseDelay = 500;

function readResponseDataFromFile(path) {
  return JSON.parse(fs.readFileSync(`./api-mocker/responses/${path}`, "utf8"));
}

const componentsData = readResponseDataFromFile(
  "components/componentsData.json"
);

const xesQueryResult = readResponseDataFromFile("xes/xes3.json");

const workspaces = [
  {
    id: "3a4c6121-4fa4-4017-b0eb-df67be468b95",
    name: "MyWorkspace1",
  },
  {
    id: "16d431d1-7ad1-475d-86a5-d4aa8578e92d",
    name: "MyOtherWorkspace",
  },
];

const dataSources = [
  {
    id: "6925b43f-cc6d-4320-8565-8388f2a7f6d7",
    organizationId: "1",
    name: "DataSource #1",
    createdAt: "2020-09-25T06:32:28Z",
  },
  {
    id: "0096ec34-dd2f-47ee-9ccf-c804637df4dc",
    organizationId: "1",
    name: "DataSource #2",
    createdAt: "2020-07-11T07:12:02Z",
  },
];

const users = [
  {
    userId: 10,
    username: "ed@example.com",
    organizationId: "1",
    organizationName: "Org1",
    organizationRole: "writer",
    password: "pass",
    locale: "en_GB",
  },
  {
    userId: 12,
    username: "bob@example.com",
    organizationId: "1",
    organizationName: "Org1",
    organizationRole: "writer",
    password: "pass",
    locale: "en_GB",
  },
  {
    userId: 28,
    username: "tyron@example.com",
    organizationId: "1",
    organizationName: "Org1",
    organizationRole: "reader",
    password: "pass",
    locale: "en_GB",
  },
  {
    userId: 29,
    username: "james@example.com",
    organizationId: "1",
    organizationName: "Org1",
    organizationRole: "reader",
    password: "pass",
    locale: "en_GB",
  },
  {
    userId: 31,
    username: "andy@example.com",
    organizationId: "1",
    organizationName: "Org1",
    organizationRole: "reader",
    password: "pass",
    locale: "en_GB",
  },
  {
    userId: 33,
    username: "eva@example.com",
    organizationId: "1",
    organizationName: "Org1",
    organizationRole: "owner",
    password: "pass",
    locale: "en_GB",
  },
];

const userSessions = [];

function createUUID() {
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, function (c) {
    const r = (Math.random() * 16) | 0,
      v = c == "x" ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

const api = {
  "GET /api/organizations/:organizationId/data-sources": { data: dataSources },
  "POST /api/organizations/:organizationId/data-sources": (req, res) => {
    const dataSource = req.body.data;

    if (!dataSource) {
      return res.status(400).json();
    }

    dataSource.id = createUUID();
    workspaces.push(dataSource);

    return res.status(201).json({ data: dataSource });
  },
  "GET /api/organizations/:organizationId/workspaces": { data: workspaces },
  "GET /api/organizations/:organizationId/workspaces/:workspaceId": (
    req,
    res
  ) => {
    const { workspaceId } = req.params;
    const workspace = _.find(workspaces, { id: workspaceId });

    if (!workspace) {
      return res.status(404).json();
    }

    return res.json({ data: workspace });
  },
  "POST /api/organizations/:organizationId/workspaces": (req, res) => {
    const workspace = req.body.data;

    if (!workspace) {
      return res.status(400).json();
    }

    workspace.id = createUUID();
    workspaces.push(workspace);

    return res.status(201).json({ data: workspace });
  },
  "PATCH /api/organizations/:organizationId/workspaces/:workspaceId": (
    req,
    res
  ) => {
    const { workspaceId } = req.params;
    const workspace = _.find(workspaces, { id: workspaceId });

    if (!workspace) {
      return res.status(404).json();
    }

    _.assign(workspace, _.pick(req.body, _.keys(workspace)));

    return res.json({ data: workspace });
  },
  "DELETE /api/organizations/:organizationId/workspaces/:workspaceId": (
    req,
    res
  ) => {
    const { workspaceId } = req.params;
    const workspaceExists = _.some(workspaces, { id: workspaceId });

    if (!workspaceExists) {
      return res.status(404).json();
    }

    _.remove(workspaces, { id: workspaceId });

    return res.status(204).json();
  },
  "GET /api/organizations/:organizationId/workspaces/:workspaceId/components": (
    req,
    res
  ) => {
    const { workspaceId } = req.params;
    const workspace = _.find(workspaces, { id: workspaceId });

    if (!workspace) {
      return res.status(404).json();
    }

    return res.json({ data: componentsData });
  },
  "GET /api/organizations/:organizationId/workspaces/:workspaceId/components/:componentId": (
    req,
    res
  ) => {
    const { workspaceId, componentId } = req.params;
    const workspace = _.find(workspaces, { id: workspaceId });
    const component = _.find(componentsData, { id: componentId });

    if (!workspace || !component) {
      return res.status(404).json();
    }

    return res.json({ data: component });
  },
  "PUT /api/organizations/:organizationId/workspaces/:workspaceId/components/:componentId": (
    req,
    res
  ) => {
    const { workspaceId, componentId } = req.params;
    const workspace = _.find(workspaces, { id: workspaceId });
    const component = _.find(componentsData, { id: componentId });

    if (!workspace || !component) {
      return res.status(404).json();
    }

    component.name = req.body.data.name;
    component.type = req.body.data.type;
    component.data = req.body.data.data;
    component.customizationData = req.body.data.customizationData;

    return res.status(204).json();
  },
  "GET /api/organizations/:organizationId/workspaces/:workspaceId/components/:componentId/data": (
    req,
    res
  ) => {
    const { workspaceId } = req.params;
    const workspace = _.find(workspaces, { id: workspaceId });

    if (!workspace) {
      return res.status(404).json();
    }

    const datasetIndex = Math.floor(componentsData.length * Math.random());

    return res.json({ data: componentsData[datasetIndex].data });
  },
  "POST /api/users/session": (req, res) => {
    const credentials = req.body.data;

    if (
      !_.some(users, { username: credentials.login }) ||
      credentials.password !==
        _.find(users, { username: credentials.login }).password
    ) {
      return res.status(401).json();
    }

    const sessionToken =
      Math.random().toString(36).substring(2, 15) +
      Math.random().toString(36).substring(2, 15);

    _.set(userSessions, sessionToken, credentials.login);

    return res.status(201).json({
      data: {
        authorizationToken: `Bearer ${sessionToken}`,
      },
    });
  },
  "DELETE /api/users/session": (req, res) => {
    const { sessionId } = req.params;

    return _.unset(userSessions, sessionId)
      ? res.status(204).json()
      : res.status(404).json();
  },
  "POST /api/users": (req, res) => {
    const { userEmail, organizationName } = req.body.data;

    if (organizationName !== "org1" && userEmail !== "user1@example.com") {
      users.push({
        userId: 1,
        username: userEmail,
        organizationId: "1",
        organizationName,
        organizationRole: "owner",
        password: "pass",
        locale: "en_GB",
      });
      res.status(201).json();
    } else {
      res.status(400).json();
    }
  },
  "GET /api/users/me": (req, res) => {
    return res.json({
      data: {
        username: _.find(users, { userId: 1 }).username,
        locale: "en-GB",
      },
    });
  },
  "PATCH /api/users/me/password": (req, res) => {
    const { currentPassword, newPassword } = req.body.data;
    const user = _.find(users, { userId: 1 });

    if (user.password !== currentPassword) {
      return res
        .status(403)
        .json({ error: "The current password is not valid" });
    }

    user.password = newPassword;

    return res.status(202).json();
  },
  "PATCH /api/users/me/locale": (req, res) => {
    const { locale } = req.body.data;
    const user = _.find(users, { userId: 1 });

    user.locale = locale;

    return res.status(202).json();
  },
  "GET /api/users/me/organizations": (req, res) => {
    const user = _.find(users, { userId: 1 });

    return res.json({
      data: [
        {
          id: user.organizationId,
          name: user.organizationName,
          organizationRole: user.organizationRole,
        },
      ],
    });
  },
  "GET /api/organizations/:organizationId/members": (req, res) => {
    const { organizationId } = req.params;
    const members = _.filter(users, { organizationId });

    if (_.isEmpty(members)) {
      return res.status(403).json();
    }

    return res.status(200).json({ data: members });
  },
  "GET /api/data-sources/:dataSourceId/logs": (req, res) => {
    const { dataSourceId } = req.params;
    const { query } = req.query;

    return res.status(200).json({ data: xesQueryResult });
  },
};

module.exports = delay(api, responseDelay);
