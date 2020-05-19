/* eslint-disable @typescript-eslint/no-var-requires */
const _ = require("lodash");

const workspaces = [
  {
    id: 1,
    name: "MyWorkspace1"
  },
  {
    id: 3,
    name: "MyOtherWorkspace"
  }
];

const users = [
  {
    userId: 10,
    username: "ed@example.com",
    organizationId: "1",
    organizationName: "Org1",
    organizationRole: "writer",
    password: "pass",
    locale: "en_GB"
  },
  {
    userId: 12,
    username: "bob@example.com",
    organizationId: "1",
    organizationName: "Org1",
    organizationRole: "writer",
    password: "pass",
    locale: "en_GB"
  },
  {
    userId: 28,
    username: "tyron@example.com",
    organizationId: "1",
    organizationName: "Org1",
    organizationRole: "reader",
    password: "pass",
    locale: "en_GB"
  },
  {
    userId: 29,
    username: "james@example.com",
    organizationId: "1",
    organizationName: "Org1",
    organizationRole: "reader",
    password: "pass",
    locale: "en_GB"
  },
  {
    userId: 31,
    username: "andy@example.com",
    organizationId: "1",
    organizationName: "Org1",
    organizationRole: "reader",
    password: "pass",
    locale: "en_GB"
  },
  {
    userId: 33,
    username: "eva@example.com",
    organizationId: "1",
    organizationName: "Org1",
    organizationRole: "owner",
    password: "pass",
    locale: "en_GB"
  }
];

const userSessions = [];

const api = {
  "GET /api/workspaces": { data: workspaces },
  "GET /api/workspaces/:workspaceId": (req, res) => {
    const { workspaceId } = req.params;
    const workspace = _.find(workspaces, { id: Number(workspaceId) });

    if (!workspace) {
      return res.status(404).json();
    }

    return res.json({ data: workspace });
  },
  "POST /api/workspaces": (req, res) => {
    const workspace = req.body;

    if (!workspace) {
      return res.status(400).json();
    }

    workspace.id = _.last(workspaces).id + 1;
    workspaces.push(workspace);

    return res.json({ data: workspace });
  },
  "PATCH /api/workspaces/:workspaceId": (req, res) => {
    const { workspaceId } = req.params;
    const workspace = _.find(workspaces, { id: Number(workspaceId) });

    if (!workspace) {
      return res.status(404).json();
    }

    _.assign(workspace, _.pick(req.body, _.keys(workspace)));

    return res.json({ data: workspace });
  },
  "DELETE /api/workspaces/:workspaceId": (req, res) => {
    const { workspaceId } = req.params;
    const workspaceExists = _.some(workspaces, { id: Number(workspaceId) });

    if (!workspaceExists) {
      return res.status(404).json();
    }

    _.remove(workspaces, { id: Number(workspaceId) });

    return res.status(204).json();
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
      Math.random()
        .toString(36)
        .substring(2, 15) +
      Math.random()
        .toString(36)
        .substring(2, 15);

    _.set(userSessions, sessionToken, credentials.login);

    return res.status(201).json({
      data: {
        authorizationToken: `Bearer ${sessionToken}`
      }
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
        locale: "en_GB"
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
        locale: "en-GB"
      }
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
          organizationRole: user.organizationRole
        }
      ]
    });
  },
  "GET /api/organizations/:organizationId/members": (req, res) => {
    const { organizationId } = req.params;
    const members = _.filter(users, { organizationId });

    if (_.isEmpty(members)) {
      return res.status(403).json();
    }

    return res.status(200).json({ data: members });
  }
};

module.exports = api;
