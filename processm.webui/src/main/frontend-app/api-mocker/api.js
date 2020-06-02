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
          depth: 0,
          outputBindings: [
            ["b"],
            ["c"],
            ["b", "d"],
            ["c", "d"],
            ["b", "c", "d"]
          ],
          inputBindings: []
        },
        { id: "b", depth: 1, outputBindings: [["e"]], inputBindings: [["a"]] },
        { id: "c", depth: 1, outputBindings: [["e"]], inputBindings: [["a"]] },
        { id: "d", depth: 1, outputBindings: [["e"]], inputBindings: [["a"]] },
        {
          id: "e",
          depth: 2,
          outputBindings: [],
          inputBindings: [["b"], ["c"], ["b", "d"], ["c", "d"], ["b", "c", "d"]]
        }
      ],

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
          depth: 0,
          outputBindings: [
            ["b", "d"],
            ["c", "d"]
          ],
          inputBindings: []
        },
        {
          id: "b",
          depth: 1,
          outputBindings: [["e"]],
          inputBindings: [["a"], ["f"]]
        },
        {
          id: "c",
          depth: 1,
          outputBindings: [["e"]],
          inputBindings: [["a"], ["f"]]
        },
        {
          id: "d",
          depth: 1,
          outputBindings: [["e"]],
          inputBindings: [["a"], ["f"]]
        },
        {
          id: "e",
          depth: 2,
          outputBindings: [["f"], ["g"], ["h"]],
          inputBindings: [
            ["b", "d"],
            ["c", "d"]
          ]
        },
        {
          id: "f",
          depth: 1,
          outputBindings: [
            ["b", "d"],
            ["c", "d"]
          ],
          inputBindings: [["e"]]
        },
        { id: "g", depth: 3, outputBindings: [["z"]], inputBindings: [["e"]] },
        { id: "h", depth: 3, outputBindings: [["z"]], inputBindings: [["e"]] },
        { id: "z", depth: 4, outputBindings: [], inputBindings: [["g"], ["h"]] }
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
        { id: "a", depth: 0, outputBindings: [["b"]], inputBindings: [] },
        {
          id: "b",
          depth: 1,
          outputBindings: [
            ["b", "c"],
            ["c", "d"]
          ],
          inputBindings: [["a"], ["b"]]
        },
        { id: "c", depth: 2, outputBindings: [["d"]], inputBindings: [["b"]] },
        {
          id: "d",
          depth: 3,
          outputBindings: [["d"], ["e"]],
          inputBindings: [
            ["b", "c"],
            ["c", "d"]
          ]
        },
        { id: "e", depth: 4, outputBindings: [], inputBindings: [["d"]] }
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
          depth: 0,
          outputBindings: [
            ["b", "c", "d"],
            ["c", "d"]
          ],
          inputBindings: []
        },
        {
          id: "b",
          depth: 1,
          outputBindings: [
            ["e", "f"],
            ["e", "g"]
          ],
          inputBindings: [["a"]]
        },
        { id: "c", depth: 1, outputBindings: [["f"]], inputBindings: [["a"]] },
        {
          id: "d",
          depth: 1,
          outputBindings: [["g", "h"]],
          inputBindings: [["a"]]
        },
        {
          id: "e",
          depth: 2,
          outputBindings: [["i"]],
          inputBindings: [["b", "g"]]
        },
        {
          id: "f",
          depth: 2,
          outputBindings: [
            ["h", "j"],
            ["m", "l"]
          ],
          inputBindings: [["b", "c"]]
        },
        {
          id: "g",
          depth: 2,
          outputBindings: [["e", "n"], ["k"], ["i"]],
          inputBindings: [["b", "d"]]
        },
        {
          id: "h",
          depth: 2,
          outputBindings: [["l"]],
          inputBindings: [["f", "d"]]
        },
        {
          id: "i",
          depth: 3,
          outputBindings: [["n"]],
          inputBindings: [["e", "g"]]
        },
        {
          id: "j",
          depth: 3,
          outputBindings: [["m"], ["n"]],
          inputBindings: [["f"]]
        },
        {
          id: "k",
          depth: 3,
          outputBindings: [["m", "n"]],
          inputBindings: [["g"]]
        },
        {
          id: "l",
          depth: 3,
          outputBindings: [["o"]],
          inputBindings: [["f"], ["h"]]
        },
        {
          id: "m",
          depth: 4,
          outputBindings: [["o"]],
          inputBindings: [["f"], ["j", "k"]]
        },
        {
          id: "n",
          depth: 4,
          outputBindings: [["o"]],
          inputBindings: [["j", "k"], ["g"]]
        },
        {
          id: "o",
          depth: 5,
          outputBindings: [],
          inputBindings: [["m"], ["n"], ["l"]]
        }
      ]
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
