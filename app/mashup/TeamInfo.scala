package lila.app
package mashup

import lila.forum.MiniForumPost
import lila.team.{ Team, RequestRepo, MemberRepo, RequestWithUser, TeamApi }
import lila.tournament.{ Tournament, TournamentRepo }
import lila.user.{ User, UserRepo }

case class TeamInfo(
    mine: Boolean,
    createdByMe: Boolean,
    requestedByMe: Boolean,
    requests: List[RequestWithUser],
    forumNbPosts: Int,
    forumPosts: List[MiniForumPost],
    teamBattles: List[Tournament]
) {

  def hasRequests = requests.nonEmpty

  def userIds = forumPosts.flatMap(_.userId)
}

final class TeamInfoApi(
    api: TeamApi,
    categApi: lila.forum.CategApi,
    forumRecent: lila.forum.Recent,
    teamCached: lila.team.Cached
) {

  def apply(team: Team, me: Option[User]): Fu[TeamInfo] = for {
    requests <- (team.enabled && me.??(m => team.isCreator(m.id))) ?? api.requestsWithUsers(team)
    mine <- me.??(m => api.belongsTo(team.id, m.id))
    requestedByMe <- !mine ?? me.??(m => RequestRepo.exists(team.id, m.id))
    forumNbPosts <- categApi.teamNbPosts(team.id)
    forumPosts <- recent.team(team.id)
    tours <- lila.tournament.TournamentRepo.byTeam(team.id, 10)
    _ <- tours.nonEmpty ?? {
      teamCached.preloadSet(tours.flatMap(_.teamBattle.??(_.teams)).toSet)
    }
  } yield TeamInfo(
    mine = mine,
    createdByMe = ~me.map(m => team.isCreator(m.id)),
    requestedByMe = requestedByMe,
    requests = requests,
    forumNbPosts = forumNbPosts,
    forumPosts = forumPosts,
    teamBattles = tours
  )
}
