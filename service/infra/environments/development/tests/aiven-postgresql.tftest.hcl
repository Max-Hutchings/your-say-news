mock_provider "aiven" {}

run "unset_cloud_selects_the_first_available_plan_region" {
  command = plan

  module {
    source = "../../modules/aiven-postgresql"
  }

  variables {
    project_name          = "your-say-news-development"
    service_name          = "your-say-news-development"
    cloud_name            = null
    available_cloud_names = ["do-lon", "upcloud-uk-lon"]
    plan                  = "free-1-1gb"
    database_name         = "your_say_news"
    database_user_names   = ["ysn_migration", "ysn_runtime"]
  }

  assert {
    condition     = aiven_pg.this.cloud_name == "do-lon"
    error_message = "An unset cloud must resolve to the first sorted region advertised for the selected plan."
  }
}

run "explicit_available_cloud_is_preserved" {
  command = plan

  module {
    source = "../../modules/aiven-postgresql"
  }

  variables {
    project_name          = "your-say-news-development"
    service_name          = "your-say-news-development"
    cloud_name            = "do-lon"
    available_cloud_names = ["do-fra", "do-lon"]
    plan                  = "free-1-1gb"
    database_name         = "your_say_news"
    database_user_names   = ["ysn_migration", "ysn_runtime"]
  }

  assert {
    condition     = aiven_pg.this.cloud_name == "do-lon"
    error_message = "An explicit cloud advertised for the selected plan must be passed to Aiven unchanged."
  }
}

run "explicit_unavailable_cloud_is_rejected" {
  command = plan

  module {
    source = "../../modules/aiven-postgresql"
  }

  variables {
    project_name          = "your-say-news-development"
    service_name          = "your-say-news-development"
    cloud_name            = "google-europe-west2"
    available_cloud_names = ["do-fra", "do-lon"]
    plan                  = "free-1-1gb"
    database_name         = "your_say_news"
    database_user_names   = ["ysn_migration", "ysn_runtime"]
  }

  expect_failures = [aiven_pg.this]
}
