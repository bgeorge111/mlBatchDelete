import React from 'react';
import { Switch, Route, Redirect } from 'react-router-dom';
import queryString from 'query-string';
import CustomSearchResult from './CustomSearchResult';
import MyDetailTemplate from './MyDetailTemplate';

import {
  SearchContainer,
  DetailContainer,
  LoginContainer
} from 'grove-core-react-redux-containers';

const PrivateRoute = ({
  component: Component,
  render,
  isAuthenticated,
  ...rest
}) => {
  return (
    <Route
      {...rest}
      render={props =>
        isAuthenticated ? (
          render ? (
            render(props)
          ) : (
            <Component {...props} />
          )
        ) : (
          <Redirect
            to={{
              pathname: '/login',
              state: { from: props.location }
            }}
          />
        )
      }
    />
  );
};

const Routes = ({ isAuthenticated }, ...rest) => {
  return (
    <Switch>
      <Route
        exact
        path="/login"
        render={props => {
          return isAuthenticated ? (
            <Redirect
              to={(props.location.state && props.location.state.from) || '/'}
            />
          ) : (
            <LoginContainer />
          );
        }}
      />
      <PrivateRoute
        isAuthenticated={isAuthenticated}
        exact
        path="/"
        render={() => <SearchContainer resultComponent={CustomSearchResult}/>}
      />
      <PrivateRoute
        isAuthenticated={isAuthenticated}
        exact
        path="/detail"
        render={props => {
          // Prefer to get id from the state
          const id =
            (props.location.state && props.location.state.id) ||
            queryString.parse(props.location.search).id;
		  const format = 
            (props.location.state && props.location.state.format) ||
            queryString.parse(props.location.search).format;
			
          return <DetailContainer template={MyDetailTemplate} id={id} />;
        }}
      />
    </Switch>
  );
};

export default Routes;
