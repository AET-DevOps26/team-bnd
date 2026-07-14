import React from "react";
import { NavLink, Outlet } from "react-router";
import { useAuth } from "react-oidc-context";
import DocumentTree from "./DocumentTree";
import $api from "../api/client";

export default function MainView() {
  const auth = useAuth();

  const {
    data: documents,
    isLoading: documentsLoading,
    error: documentsError,
  } = $api.useQuery("get", "/api/v1/knowledgebase/documents");

  return (
    <div className="app">
      <header className="app-header">
        <div className="app-header-top">
          <h1>Alexandria</h1>
          <nav className="app-header-nav">
            <NavLink
              to="/ask"
              className={({ isActive }) =>
                `app-tab${isActive ? " app-tab--active" : ""}`
              }
            >
              <span className="app-tab__label">Ask</span>
              <span className="app-tab__sizer">Ask</span>
            </NavLink>
            <NavLink
              to="/documents"
              end={false}
              className={({ isActive }) =>
                `app-tab${isActive ? " app-tab--active" : ""}`
              }
            >
              <span className="app-tab__label">Documents</span>
              <span className="app-tab__sizer">Documents</span>
            </NavLink>
          </nav>
          <div className="user-info">
            <span className="user-name">
              {auth.user?.profile.preferred_username ?? auth.user?.profile.sub}
            </span>
            <button className="logout-button" onClick={() => auth.removeUser()}>
              Logout
            </button>
          </div>
        </div>
      </header>
      <div className="app-body">
        <DocumentTree
          documents={documents}
          isLoading={documentsLoading}
          error={documentsError}
        />
        <main className="app-main">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
