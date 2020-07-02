/* eslint-disable @typescript-eslint/no-var-requires */
const _ = require("lodash");

const workspaces = [
  {
    id: "3a4c6121-4fa4-4017-b0eb-df67be468b95",
    name: "MyWorkspace1"
  },
  {
    id: "16d431d1-7ad1-475d-86a5-d4aa8578e92d",
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

const componentsData = [
  {
    id: "cf607cb0-0b88-4ccd-9795-5cd0201b3c39",
    name: "some name 1",
    type: "casualNet",
    data: {
      query: "SELECT ...",
      nodes: [
        {
          id: "a",
          splits: [["c"], ["d", "b", "c"], ["b", "d"], ["b"], ["c", "d"]],
          joins: []
        },
        { id: "b", splits: [["e"]], joins: [["a"]] },
        { id: "c", splits: [["e"]], joins: [["a"]] },
        { id: "d", splits: [["e"]], joins: [["a"]] },
        {
          id: "e",
          splits: [],
          joins: [["c", "d"], ["b"], ["c", "b", "d"], ["c"], ["b", "d"]]
        }
      ],
      edges: [
        {
          sourceNodeId: "a",
          targerNodeId: "b"
        },
        {
          sourceNodeId: "a",
          targerNodeId: "c"
        },
        {
          sourceNodeId: "a",
          targerNodeId: "d"
        },
        {
          sourceNodeId: "b",
          targerNodeId: "e"
        },
        {
          sourceNodeId: "c",
          targerNodeId: "e"
        },
        {
          sourceNodeId: "d",
          targerNodeId: "e"
        }
      ]
    }
  },
  {
    id: "c629ba68-aae2-4490-85cd-3875939fc69e",
    name: "component 1",
    type: "casualNet",
    data: {
      query: "SELECT ...",
      nodes: [
        {
          id: "a",
          splits: [
            ["b", "d"],
            ["c", "d"]
          ],
          joins: []
        },
        {
          id: "b",
          splits: [["e"]],
          joins: [["a"], ["f"]]
        },
        {
          id: "c",
          splits: [["e"]],
          joins: [["a"], ["f"]]
        },
        {
          id: "d",
          splits: [["e"]],
          joins: [["a"], ["f"]]
        },
        {
          id: "e",
          splits: [["f"], ["g"], ["h"]],
          joins: [
            ["b", "d"],
            ["c", "d"]
          ]
        },
        {
          id: "f",
          splits: [
            ["b", "d"],
            ["c", "d"]
          ],
          joins: [["e"]]
        },
        { id: "g", splits: [["z"]], joins: [["e"]] },
        { id: "h", splits: [["z"]], joins: [["e"]] },
        { id: "z", splits: [], joins: [["g"], ["h"]] }
      ]
    }
  },
  {
    id: "93bcc033-bbea-490d-93b9-3c8580db5354",
    name: "long long long long name",
    type: "casualNet",
    data: {
      query: "SELECT ...",
      nodes: [
        { id: "a", splits: [["b"]], joins: [] },
        {
          id: "b",
          splits: [
            ["b", "c"],
            ["c", "d"]
          ],
          joins: [["a"], ["b"]]
        },
        { id: "c", splits: [["d"]], joins: [["b"]] },
        {
          id: "d",
          splits: [["d"], ["e"]],
          joins: [
            ["b", "c"],
            ["c", "d"]
          ]
        },
        { id: "e", splits: [], joins: [["d"]] }
      ],
      edges: [
        {
          sourceNodeId: "a",
          targerNodeId: "b"
        },
        {
          sourceNodeId: "b",
          targerNodeId: "b"
        },
        {
          sourceNodeId: "b",
          targerNodeId: "c"
        },
        {
          sourceNodeId: "b",
          targerNodeId: "d"
        },
        {
          sourceNodeId: "c",
          targerNodeId: "d"
        },
        {
          sourceNodeId: "d",
          targerNodeId: "d"
        },
        {
          sourceNodeId: "d",
          targerNodeId: "e"
        }
      ],
      layout: [
        { id: "a", x: 125, y: 25 },
        { id: "b", x: 125, y: 75 },
        { id: "c", x: 160, y: 125 },
        { id: "d", x: 125, y: 175 },
        { id: "e", x: 125, y: 225 }
      ]
    }
  },
  {
    id: "f7bad885-6add-4516-b536-d6f88c154b6e",
    name: "c1",
    type: "casualNet",
    data: {
      query: "SELECT ...",
      nodes: [
        {
          id: "a",
          splits: [
            ["b", "c", "d"],
            ["c", "d"]
          ],
          joins: []
        },
        {
          id: "b",
          splits: [
            ["e", "f"],
            ["e", "g"]
          ],
          joins: [["a"]]
        },
        { id: "c", splits: [["f"]], joins: [["a"]] },
        {
          id: "d",
          splits: [["g", "h"]],
          joins: [["a"]]
        },
        {
          id: "e",
          splits: [["i"]],
          joins: [["b", "g"]]
        },
        {
          id: "f",
          splits: [
            ["h", "j"],
            ["m", "l"]
          ],
          joins: [["b", "c"]]
        },
        {
          id: "g",
          splits: [["e", "n"], ["k"], ["i"]],
          joins: [["b", "d"]]
        },
        {
          id: "h",
          splits: [["l"]],
          joins: [["f", "d"]]
        },
        {
          id: "i",
          splits: [["n"]],
          joins: [["e", "g"]]
        },
        {
          id: "j",
          splits: [["m"], ["n"]],
          joins: [["f"]]
        },
        {
          id: "k",
          splits: [["m", "n"]],
          joins: [["g"]]
        },
        {
          id: "l",
          splits: [["o"]],
          joins: [["f"], ["h"]]
        },
        {
          id: "m",
          splits: [["o"]],
          joins: [["f"], ["j", "k"]]
        },
        {
          id: "n",
          splits: [["o"]],
          joins: [["j", "k"], ["g"]]
        },
        {
          id: "o",
          splits: [],
          joins: [["m"], ["n"], ["l"]]
        }
      ]
    }
  },
  {
    id: "06a29e69-a58e-498c-ad66-6435816d9c7b",
    name: "KPI example",
    type: "kpi",
    data: {
      query: "SELECT ...",
      value: 98.9
    }
  }
];

const userSessions = [];

function createUUID() {
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, function(c) {
    const r = (Math.random() * 16) | 0,
      v = c == "x" ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

const api = {
  "GET /api/workspaces": { data: workspaces },
  "GET /api/workspaces/:workspaceId": (req, res) => {
    const { workspaceId } = req.params;
    const workspace = _.find(workspaces, { id: workspaceId });

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

    workspace.id = createUUID();
    workspaces.push(workspace);

    return res.json({ data: workspace });
  },
  "PATCH /api/workspaces/:workspaceId": (req, res) => {
    const { workspaceId } = req.params;
    const workspace = _.find(workspaces, { id: workspaceId });

    if (!workspace) {
      return res.status(404).json();
    }

    _.assign(workspace, _.pick(req.body, _.keys(workspace)));

    return res.json({ data: workspace });
  },
  "DELETE /api/workspaces/:workspaceId": (req, res) => {
    const { workspaceId } = req.params;
    const workspaceExists = _.some(workspaces, { id: workspaceId });

    if (!workspaceExists) {
      return res.status(404).json();
    }

    _.remove(workspaces, { id: workspaceId });

    return res.status(204).json();
  },
  "GET /api/workspaces/:workspaceId/components/:componentId": (req, res) => {
    const { workspaceId, componentId } = req.params;
    const workspace = _.find(workspaces, { id: workspaceId });
    const component = _.find(componentsData, { id: componentId });

    if (!workspace || !component) {
      return res.status(404).json();
    }

    return res.json({ data: component });
  },
  "GET /api/workspaces/:workspaceId/components/:componentId/data": (
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
